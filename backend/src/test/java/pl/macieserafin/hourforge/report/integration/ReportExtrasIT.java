package pl.macieserafin.hourforge.report.integration;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.jira.JiraConnection;
import pl.macieserafin.hourforge.jira.JiraConnectionRepository;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.IssueLink;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportExtrasIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TimeEntryRepository entries;
    @Autowired JiraConnectionRepository connections;

    private final LocalDate monday = LocalDate.of(2026, 10, 19);
    private User owner;
    private User admin;
    private User worker;
    private User member;
    private Team team;
    private Project project;
    private JiraConnection connection;
    private String base;

    @BeforeEach
    void setup() {
        owner = fixtures.user("extras-owner@example.com", "Owner");
        admin = fixtures.user("extras-admin@example.com", "Admin");
        worker = fixtures.user("extras-worker@example.com", "Anna");
        member = fixtures.user("extras-member@example.com", "Zofia");
        team = fixtures.team("Extras team", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, admin, TeamRole.ADMIN, Instant.now());
        fixtures.member(team, worker, TeamRole.MEMBER, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
        project = fixtures.project(team, "Main", null);
        connection = connections.saveAndFlush(new JiraConnection(team, "cloud-1", "https://team.atlassian.net", "Site", owner));
        base = "/api/teams/" + team.getId() + "/reports";
        entry(worker, monday, 3600, "PROJ-1", "10001");
        entry(worker, monday, 1800, "PROJ-1", "10001");
        entry(worker, monday.plusDays(2), 600, "PROJ-2", "10002");
        entry(member, monday.plusDays(2), 900, null, null);
        entry(member, monday.plusDays(8), 100, "PROJ-1", "10001");
    }

    private void entry(User person, LocalDate day, int seconds, String key, String issueId) {
        var entry = new TimeEntry(person, team, project, EntryType.MANUAL, day, null, null, seconds, "=SUM(A1) & <b>text</b>");
        if (key != null) entry.setJiraIssue(new IssueLink(connection.getId(), issueId, key));
        entries.saveAndFlush(entry);
    }

    private MockHttpServletRequestBuilder asAdmin(String path) {
        return get(base + path).with(user(new AppUserPrincipal(admin.getId(), admin.getEmail(), "unused"))).with(csrf());
    }

    private MockHttpServletRequestBuilder week(String path) {
        return asAdmin(path).param("from", monday.toString()).param("to", monday.plusDays(6).toString());
    }

    @Test
    void timeIsGroupedAndFilteredByJiraTaskIncludingEntriesWithoutOne() throws Exception {
        mvc.perform(week("/breakdown").param("groupBy", "JIRA_ISSUE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.content[?(@.jiraIssueKey=='PROJ-1')].durationSeconds", contains(5400)))
                .andExpect(jsonPath("$.data.content[?(@.jiraIssueKey=='PROJ-2')].durationSeconds", contains(600)))
                .andExpect(jsonPath("$.data.content[?(@.jiraIssueKey==null)].durationSeconds", contains(900)));
        mvc.perform(week("/summary").param("issueKey", " proj-1 ")).andExpect(jsonPath("$.totals.durationSeconds").value(5400))
                .andExpect(jsonPath("$.context.filter.issueKey").value("PROJ-1"));
        mvc.perform(week("/entries").param("issueKey", "PROJ-2")).andExpect(jsonPath("$.data.content", hasSize(1)));
        mvc.perform(week("/summary").param("issueKey", "not a key")).andExpect(status().isBadRequest());
        mvc.perform(week("/summary").param("issueKey", "PROJ-999")).andExpect(jsonPath("$.totals.entryCount").value(0));
    }

    @Test
    void theTimesheetShowsEachPersonByDayWithTotalsForTheOwnerAndAdminsOnly() throws Exception {
        mvc.perform(week("/timesheet")).andExpect(status().isOk())
                .andExpect(jsonPath("$.days", hasSize(7))).andExpect(jsonPath("$.days[0]").value("2026-10-19"))
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.rows[0].displayName").value("Anna")).andExpect(jsonPath("$.rows[0].seconds", contains(5400, 0, 600, 0, 0, 0, 0)))
                .andExpect(jsonPath("$.rows[0].total").value(6000))
                .andExpect(jsonPath("$.rows[1].displayName").value("Zofia")).andExpect(jsonPath("$.rows[1].seconds[2]").value(900))
                .andExpect(jsonPath("$.dayTotals", contains(5400, 0, 1500, 0, 0, 0, 0))).andExpect(jsonPath("$.total").value(6900))
                .andExpect(jsonPath("$.truncated").value(false));
        mvc.perform(get(base + "/timesheet").with(user(new AppUserPrincipal(worker.getId(), worker.getEmail(), "unused")))).andExpect(status().isForbidden());
    }

    @Test
    void theTimesheetNeedsADatedRangeOfAtMostAMonthAndFollowsThePersonFilter() throws Exception {
        mvc.perform(asAdmin("/timesheet").param("undated", "true")).andExpect(status().isBadRequest());
        mvc.perform(asAdmin("/timesheet").param("from", "2026-09-01").param("to", "2026-10-31")).andExpect(status().isBadRequest());
        mvc.perform(week("/timesheet").param("userIds", member.getId().toString())).andExpect(jsonPath("$.rows", hasSize(1)))
                .andExpect(jsonPath("$.rows[0].displayName").value("Zofia"));
        mvc.perform(asAdmin("/timesheet").param("from", "2026-12-01").param("to", "2026-12-03")).andExpect(jsonPath("$.rows", hasSize(0)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void theExcelExportHasTheCsvRowsAsPlainTextAndIsAValidWorkbook() throws Exception {
        var response = mvc.perform(week("/export.xlsx")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", containsString("hourforge-raport-2026-10-19_2026-10-25.xlsx")))
                .andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn().getResponse().getContentAsByteArray();
        var parts = new java.util.HashMap<String, String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(response))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) parts.put(entry.getName(), new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        assertThat(parts).containsKeys("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/styles.xml");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        for (var xml : parts.values()) factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var sheet = parts.get("xl/worksheets/sheet1.xml");
        assertThat(sheet).contains("Zadanie Jira").contains("PROJ-1").contains("Extras team").contains("Anna");
        // Text that looks like a formula or markup is kept as text and escaped, never evaluated.
        assertThat(sheet).contains("=SUM(A1) &amp; &lt;b&gt;text&lt;/b&gt;").doesNotContain("<f>");
        assertThat(sheet).contains("<v>3600</v>");
        mvc.perform(get(base + "/export.xlsx").with(user(new AppUserPrincipal(worker.getId(), worker.getEmail(), "unused")))).andExpect(status().isForbidden());
    }

    @Test
    void theExcelExportRefusesToDropRowsLikeTheCsv() throws Exception {
        // The default limit is far above this data set; an unreachable range is simply empty.
        var empty = mvc.perform(asAdmin("/export.xlsx").param("from", "2026-12-01").param("to", "2026-12-03")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray();
        assertThat(empty).isNotEmpty();
    }
}
