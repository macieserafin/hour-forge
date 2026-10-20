package pl.macieserafin.hourforge.report.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportPdfIT extends ApiIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired TimeEntryRepository entries;
    @Autowired TeamMemberRepository members;
    @MockitoBean Clock clock;
    private User owner;
    private User worker;
    private Team team;
    private Project project;
    private Project other;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        owner = fixtures.user("pdf-owner@example.test", "Właściciel Żółć");
        worker = fixtures.user("pdf-worker@example.test", "Anna Łukasiewicz");
        team = fixtures.team("Zespół Źdźbło", "Europe/Warsaw");
        fixtures.member(team, owner, TeamRole.OWNER, NOW.minusSeconds(10000));
        fixtures.member(team, worker, TeamRole.MEMBER, NOW.minusSeconds(10000));
        project = fixtures.project(team, "Strona główna", null);
        other = fixtures.project(team, "Archiwum", null);
    }

    private AppUserPrincipal principal(User actor) {
        return new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused");
    }

    private MockHttpServletRequestBuilder teamPdf(User actor) {
        return get("/api/teams/" + team.getId() + "/reports/export.pdf").with(user(principal(actor)));
    }

    private MockHttpServletRequestBuilder ownPdf(User actor) {
        return get("/api/teams/" + team.getId() + "/reports/mine/export.pdf").with(user(principal(actor)));
    }

    private void manual(User author, Project target, String date, int seconds, String description) {
        entries.saveAndFlush(new TimeEntry(author, target.getTeam(), target, EntryType.MANUAL,
                date == null ? null : LocalDate.parse(date), null, null, seconds, description));
    }

    private String text(MockHttpServletRequestBuilder request) throws Exception {
        var bytes = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        try (var document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    private int pages(MockHttpServletRequestBuilder request) throws Exception {
        try (var document = Loader.loadPDF(mvc.perform(request).andReturn().getResponse().getContentAsByteArray())) {
            return document.getNumberOfPages();
        }
    }

    @Test
    void teamReportShowsTotalsGroupsAndEntriesWithPolishLettersAndDownloadHeaders() throws Exception {
        manual(owner, project, "2026-10-19", 3725, "Zażółć gęślą jaźń");
        manual(worker, other, "2026-10-20", 600, "Spotkanie zespołu");
        manual(worker, project, null, 60, "Wpis bez daty");
        entries.saveAndFlush(new TimeEntry(owner, team, project, EntryType.TIMER, LocalDate.parse("2026-10-20"),
                NOW.minusSeconds(100), null, null, "W toku"));
        var foreign = fixtures.team("Obcy zespół", "UTC");
        manual(worker, fixtures.project(foreign, "Obcy projekt", null), "2026-10-20", 9000, "Praca w obcym zespole");
        other.setArchivedAt(NOW);

        mvc.perform(teamPdf(owner)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"hourforge-raport-2026-10-19_2026-10-25.pdf\""))
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        var content = text(teamPdf(owner));
        assertThat(content).contains("Raport czasu pracy zespołu", "Zespół: Zespół Źdźbło",
                "Zakres: 2026-10-19 – 2026-10-25 (strefa czasowej zespołu: Europe/Warsaw)".replace("czasowej", "czasowa"),
                "Łączny czas: 01:12:05", "Liczba wpisów: 2", "Czas według osób", "Czas według projektów", "Czas według dni",
                "Właściciel Żółć", "Anna Łukasiewicz", "Strona główna", "Archiwum (archiwalny)",
                "Zażółć gęślą jaźń", "Spotkanie zespołu", "2026-10-19", "Strona 1 z 1", "wygenerowano 2026-10-19 12:00");
        assertThat(content).doesNotContain("Praca w obcym zespole", "W toku", "Wpis bez daty");
    }

    @Test
    void appliesFiltersAndNamesThem() throws Exception {
        manual(owner, project, "2026-10-19", 60, "Faktura A");
        manual(worker, project, "2026-10-20", 120, "Faktura B");
        manual(worker, other, "2026-10-21", 180, "Spotkanie");
        var content = text(teamPdf(owner).param("q", "faktura").param("userIds", worker.getId().toString())
                .param("projectIds", project.getId().toString()).param("entryType", "MANUAL"));
        assertThat(content).contains("Filtry: osoby: Anna Łukasiewicz; projekty: Strona główna; rodzaj: Wpis ręczny; opis zawiera „faktura”",
                "Faktura B", "Liczba wpisów: 1");
        assertThat(content).doesNotContain("Faktura A", "Spotkanie");
    }

    @Test
    void undatedReportsSayWhichEntriesTheyCover() throws Exception {
        manual(worker, project, null, 60, "Wpis bez daty");
        manual(worker, project, "2026-10-20", 60, "Wpis z datą");
        var content = text(teamPdf(owner).param("undated", "true"));
        assertThat(content).contains("Zakres: wpisy bez daty", "Wpis bez daty").doesNotContain("Wpis z datą");
        mvc.perform(teamPdf(owner).param("undated", "true")).andExpect(header().string("Content-Disposition", containsString("bez-daty.pdf")));
    }

    @Test
    void anEmptyReportStillProducesADocument() throws Exception {
        var content = text(teamPdf(owner));
        assertThat(content).contains("Łączny czas: 00:00:00", "Liczba wpisów: 0", "Brak wpisów dla wybranych filtrów.");
    }

    @Test
    void charactersMissingFromTheFontDoNotBreakTheReport() throws Exception {
        // The embedded font has no CJK glyphs; control characters are dropped and tabs become spaces.
        manual(worker, project, "2026-10-19", 60, "Znak 漢 oraz \u0001 sterujący i tabulator\tw tekście");
        var content = text(teamPdf(owner));
        assertThat(content).contains("Znak ? oraz sterujący i tabulator w tekście");
    }

    @Test
    void longReportsContinueOnFurtherPagesWithTheTableHeaderRepeated() throws Exception {
        for (int index = 0; index < 120; index++) {
            manual(index % 2 == 0 ? owner : worker, project, "2026-10-19", 60 + index,
                    "Wpis numer " + index + " z długim opisem, który musi zostać zawinięty do kilku linii, bo ma bardzo wiele słów "
                            + "i niepodzielneSłowoKtóreJestZdecydowanieDłuższeNiżKolumnaOpisuMożePomieścićWJednejLiniiTekstuAbyPrzetestowaćPodział");
        }
        var request = teamPdf(owner);
        int pages = pages(request);
        assertThat(pages).isGreaterThan(2);
        var content = text(request);
        assertThat(content).contains("Strona 1 z " + pages, "Strona " + pages + " z " + pages, "Wpis numer 0", "Wpis numer 119");
        assertThat(content.split("Data pracy", -1).length - 1).isGreaterThan(2);
    }

    @Test
    void memberDownloadsOnlyTheirOwnEntriesAndTheOwnerCanDownloadTheirsToo() throws Exception {
        manual(owner, project, "2026-10-19", 60, "Praca właściciela");
        manual(worker, project, "2026-10-20", 120, "Praca Anny");
        var own = text(ownPdf(worker).param("userIds", owner.getId().toString()));
        assertThat(own).contains("Mój raport czasu pracy", "Osoba: Anna Łukasiewicz", "Praca Anny", "Liczba wpisów: 1")
                .doesNotContain("Praca właściciela", "Czas według osób", "Filtry: osoby");
        mvc.perform(ownPdf(worker)).andExpect(header().string("Content-Disposition", containsString("hourforge-moj-raport-2026-10-19_2026-10-25.pdf")));
        assertThat(text(ownPdf(owner))).contains("Praca właściciela").doesNotContain("Praca Anny");
    }

    @Test
    void permissionsAndValidationFollowTheOtherReports() throws Exception {
        mvc.perform(get("/api/teams/" + team.getId() + "/reports/export.pdf")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/teams/" + team.getId() + "/reports/mine/export.pdf")).andExpect(status().isUnauthorized());
        mvc.perform(teamPdf(worker)).andExpect(status().isForbidden());
        var stranger = fixtures.user("pdf-stranger@example.test", "Stranger");
        mvc.perform(teamPdf(stranger)).andExpect(status().isNotFound());
        mvc.perform(ownPdf(stranger)).andExpect(status().isNotFound());
        members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow().setLeftAt(NOW);
        mvc.perform(ownPdf(worker)).andExpect(status().isNotFound());
        mvc.perform(teamPdf(owner).param("from", "2025-10-19").param("to", "2026-10-20")).andExpect(status().isBadRequest());
        mvc.perform(ownPdf(owner).param("q", "x".repeat(201))).andExpect(status().isBadRequest());
        mvc.perform(teamPdf(owner).param("projectIds", UUID.randomUUID().toString())).andExpect(status().isNotFound());
    }
}
