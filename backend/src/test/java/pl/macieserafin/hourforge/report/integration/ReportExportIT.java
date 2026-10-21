package pl.macieserafin.hourforge.report.integration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

class ReportExportIT extends ApiIntegrationTestSupport {
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
        owner = fixtures.user("export-owner@example.test", "Właściciel Żółć");
        worker = fixtures.user("export-worker@example.test", "Worker");
        team = fixtures.team("Zespół; Test", "Europe/Warsaw");
        fixtures.member(team, owner, TeamRole.OWNER, NOW.minusSeconds(10000));
        fixtures.member(team, worker, TeamRole.MEMBER, NOW.minusSeconds(10000));
        project = fixtures.project(team, "Strona", null);
        other = fixtures.project(team, "Inny", null);
    }

    private AppUserPrincipal principal(User actor) {
        return new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused");
    }

    private MockHttpServletRequestBuilder csv(User actor) {
        return get("/api/teams/" + team.getId() + "/reports/export.csv").with(user(principal(actor)));
    }

    private TimeEntry manual(User author, Project target, String date, int seconds, String description) {
        return entries.saveAndFlush(new TimeEntry(author, target.getTeam(), target, EntryType.MANUAL,
                date == null ? null : LocalDate.parse(date), null, null, seconds, description));
    }

    private List<String> lines(String body) {
        return body.lines().toList();
    }

    private String download(MockHttpServletRequestBuilder request) throws Exception {
        var bytes = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    @Test
    void exportsTheWholeFilteredResultAsUtf8CsvOldestFirstWithEscaping() throws Exception {
        manual(owner, project, "2026-10-21", 3725, "Zażółć gęślą jaźń");
        manual(worker, other, "2026-10-19", 60, "=HYPERLINK(\"http://x\")");
        manual(worker, project, "2026-10-20", 90, "Line one\nline; two");
        entries.saveAndFlush(new TimeEntry(worker, team, project, EntryType.TIMER, LocalDate.parse("2026-10-22"),
                NOW.minusSeconds(300), NOW, 300, null));
        entries.saveAndFlush(new TimeEntry(owner, team, project, EntryType.TIMER, LocalDate.parse("2026-10-20"),
                NOW.minusSeconds(100), null, null, "Still running"));
        manual(worker, project, "2026-11-30", 500, "Outside the default week");
        var foreign = fixtures.team("Foreign", "UTC");
        manual(worker, fixtures.project(foreign, "Foreign project", null), "2026-10-20", 9000, "Foreign work");

        mvc.perform(csv(owner)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Type", containsString("UTF-8")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"hourforge-raport-2026-10-19_2026-10-25.csv\""))
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        var body = download(csv(owner));
        assertThat(body).endsWith("\r\n");
        assertThat(body.split("\r\n")).containsExactly(
                "Data pracy;Osoba;Zespół;Projekt;Tag;Zadanie Jira;Opis;Rodzaj;Czas (gg:mm:ss);Czas (sekundy)",
                "2026-10-19;Worker;\"Zespół; Test\";Inny;;;\"'=HYPERLINK(\"\"http://x\"\")\";Wpis ręczny;00:01:00;60",
                "2026-10-20;Worker;\"Zespół; Test\";Strona;;;\"Line one\nline; two\";Wpis ręczny;00:01:30;90",
                "2026-10-21;Właściciel Żółć;\"Zespół; Test\";Strona;;;Zażółć gęślą jaźń;Wpis ręczny;01:02:05;3725",
                "2026-10-22;Worker;\"Zespół; Test\";Strona;;;;Timer;00:05:00;300");
    }

    @Test
    void appliesTheSameFiltersAsTheOnScreenReport() throws Exception {
        manual(owner, project, "2026-10-19", 60, "Faktura A");
        manual(worker, project, "2026-10-20", 120, "faktura B");
        manual(worker, other, "2026-10-21", 180, "Spotkanie");
        manual(worker, project, null, 30, "Faktura bez daty");

        assertThat(lines(download(csv(owner).param("q", "FAKTURA")))).hasSize(3);
        assertThat(lines(download(csv(owner).param("userIds", worker.getId().toString())))).hasSize(3);
        assertThat(lines(download(csv(owner).param("projectIds", other.getId().toString())))).hasSize(2);
        assertThat(lines(download(csv(owner).param("entryType", "TIMER")))).hasSize(1);
        assertThat(lines(download(csv(owner).param("from", "2026-10-20").param("to", "2026-10-20")))).hasSize(2);
        var undated = csv(owner).param("undated", "true");
        mvc.perform(undated).andExpect(header().string("Content-Disposition", containsString("hourforge-raport-bez-daty.csv")));
        assertThat(lines(download(undated))).hasSize(2).last().asString().startsWith(";Worker;");
    }

    @Test
    void includesFormerMembersAndArchivedProjects() throws Exception {
        manual(worker, project, "2026-10-19", 60, "Before leaving");
        project.setArchivedAt(NOW);
        members.findByTeamIdAndUserId(team.getId(), worker.getId()).orElseThrow().setLeftAt(NOW);
        assertThat(lines(download(csv(owner)))).hasSize(2);
    }

    @Test
    void rejectsEveryoneButTheOwnerAndInvalidRequests() throws Exception {
        mvc.perform(get("/api/teams/" + team.getId() + "/reports/export.csv")).andExpect(status().isUnauthorized());
        mvc.perform(csv(worker)).andExpect(status().isForbidden());
        var stranger = fixtures.user("export-stranger@example.test", "Stranger");
        mvc.perform(csv(stranger)).andExpect(status().isNotFound());
        mvc.perform(csv(owner).param("from", "2025-10-19").param("to", "2026-10-20")).andExpect(status().isBadRequest());
        mvc.perform(csv(owner).param("q", "x".repeat(201))).andExpect(status().isBadRequest());
        mvc.perform(csv(owner).param("undated", "true").param("from", "2026-10-19").param("to", "2026-10-20")).andExpect(status().isBadRequest());
        mvc.perform(csv(owner).param("userIds", java.util.UUID.randomUUID().toString())).andExpect(status().isNotFound());
    }

    @Test
    void limitsHowOftenAReportCanBeDownloaded() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) mvc.perform(csv(owner)).andExpect(status().isOk());
        mvc.perform(csv(owner)).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }
}
