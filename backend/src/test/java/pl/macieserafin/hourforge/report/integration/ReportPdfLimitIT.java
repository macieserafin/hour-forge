package pl.macieserafin.hourforge.report.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.report-export.pdf-max-entries=2")
class ReportPdfLimitIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TimeEntryRepository entries;
    @MockitoBean Clock clock;

    @Test
    void cutsAVeryLongEntryListAndSaysHowMuchWasLeftOut() throws Exception {
        var now = Instant.parse("2026-10-19T10:00:00Z");
        when(clock.instant()).thenReturn(now);
        var owner = fixtures.user("pdf-limit-owner@example.test", "Owner");
        var team = fixtures.team("Limit", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, now.minusSeconds(100));
        var project = fixtures.project(team, "Project", null);
        for (int index = 0; index < 5; index++) {
            entries.saveAndFlush(new TimeEntry(owner, team, project, EntryType.MANUAL, LocalDate.parse("2026-10-19"), null, null, 60, "Row " + index));
        }
        var principal = new AppUserPrincipal(owner.getId(), owner.getEmail(), "unused");
        var bytes = mvc.perform(get("/api/teams/" + team.getId() + "/reports/export.pdf").with(user(principal)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var document = Loader.loadPDF(bytes)) {
            var text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(text).contains("Pokazano pierwsze 2 z 5 wpisów. Pełną listę zawiera eksport CSV.", "Liczba wpisów: 5");
            assertThat(text).doesNotContain("Row 4");
        }
    }
}
