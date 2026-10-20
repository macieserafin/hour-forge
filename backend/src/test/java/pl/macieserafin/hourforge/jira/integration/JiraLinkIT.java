package pl.macieserafin.hourforge.jira.integration;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.jira.JiraAtlassianClient;
import pl.macieserafin.hourforge.jira.JiraConnection;
import pl.macieserafin.hourforge.jira.JiraConnectionRepository;
import pl.macieserafin.hourforge.jira.JiraSearchCache;
import pl.macieserafin.hourforge.jira.JiraTokenService;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JiraLinkIT extends JiraIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired JiraConnectionRepository connections;
    @Autowired JiraTokenService tokens;
    @Autowired JiraAtlassianClient atlassian;
    @Autowired JiraSearchCache cache;

    private User owner;
    private User member;
    private User unconnected;
    private Team team;
    private Project project;
    private JiraConnection connection;
    private AppUserPrincipal ownerPrincipal;
    private AppUserPrincipal memberPrincipal;
    private AppUserPrincipal unconnectedPrincipal;

    @BeforeEach
    void setup() {
        cache.clear();
        owner = fixtures.user("owner@example.com", "Owner");
        member = fixtures.user("member@example.com", "Member");
        unconnected = fixtures.user("unconnected@example.com", "Unconnected");
        team = fixtures.team("Studio", "Europe/Warsaw");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
        fixtures.member(team, unconnected, TeamRole.MEMBER, Instant.now());
        project = fixtures.project(team, "Website", null);
        connection = connections.saveAndFlush(new JiraConnection(team, "cloud-1", "https://team.atlassian.net", "Team site", owner));
        tokens.store(member, "cloud-1", atlassian.exchangeCode("good-code"));
        tokens.store(owner, "cloud-1", atlassian.exchangeCode("good-code"));
        ownerPrincipal = new AppUserPrincipal(owner.getId(), owner.getEmail(), "unused");
        memberPrincipal = new AppUserPrincipal(member.getId(), member.getEmail(), "unused");
        unconnectedPrincipal = new AppUserPrincipal(unconnected.getId(), unconnected.getEmail(), "unused");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, AppUserPrincipal principal, String body) throws Exception {
        entityManager.flush();
        return mvc.perform(request.with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String manualBody(String jiraIssueId) {
        return "{\"teamId\":\"" + team.getId() + "\",\"projectId\":\"" + project.getId() + "\",\"workDate\":\"" + LocalDate.now()
                + "\",\"durationSeconds\":600,\"description\":\"Work\"" + (jiraIssueId == null ? "" : ",\"jiraIssueId\":\"" + jiraIssueId + "\"") + "}";
    }

    private String updateBody(String jiraIssueId) {
        return "{\"projectId\":\"" + project.getId() + "\",\"workDate\":\"" + LocalDate.now() + "\",\"durationSeconds\":600,\"description\":\"Edited\""
                + (jiraIssueId == null ? "" : ",\"jiraIssueId\":\"" + jiraIssueId + "\"") + "}";
    }

    private String createManual(AppUserPrincipal principal, String jiraIssueId) throws Exception {
        var body = send(post("/api/time-entries"), principal, manualBody(jiraIssueId)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void aManualEntryStoresTheIssueKeyJiraReportsNotTheOneTheClientClaims() throws Exception {
        var body = "{\"teamId\":\"" + team.getId() + "\",\"projectId\":\"" + project.getId() + "\",\"workDate\":\"" + LocalDate.now()
                + "\",\"durationSeconds\":600,\"jiraIssueId\":\"10002\",\"jiraIssueKey\":\"EVIL-1\"}";
        send(post("/api/time-entries"), memberPrincipal, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.jiraIssueId").value("10002")).andExpect(jsonPath("$.jiraIssueKey").value("PROJ-2"));
        assertThat(jdbc.queryForMap("select jira_connection_id, jira_issue_id, jira_issue_key from time_entries where user_id = ?", member.getId()))
                .containsEntry("jira_connection_id", connection.getId()).containsEntry("jira_issue_id", "10002").containsEntry("jira_issue_key", "PROJ-2");
        assertThat(ATLASSIAN.issueCalls.get()).isEqualTo(1);
    }

    @Test
    void entriesWithoutALinkNeverCallJira() throws Exception {
        send(post("/api/time-entries"), memberPrincipal, manualBody(null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.jiraIssueKey").doesNotExist());
        assertThat(ATLASSIAN.issueCalls.get()).isZero();
    }

    @Test
    void aTimerCarriesTheLinkIntoTheHistory() throws Exception {
        var started = send(post("/api/timer/start"), memberPrincipal,
                "{\"teamId\":\"" + team.getId() + "\",\"projectId\":\"" + project.getId() + "\",\"jiraIssueId\":\"10001\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.jiraIssueKey").value("PROJ-1")).andReturn().getResponse().getContentAsString();
        var entryId = JsonPath.<String>read(started, "$.id");
        jdbc.update("update time_entries set started_at = now() - interval '5 minutes' where id = ?::uuid", entryId);
        entityManager.clear();
        send(post("/api/timer/stop"), memberPrincipal, "{\"entryId\":\"" + entryId + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.jiraIssueKey").value("PROJ-1"));
        entityManager.flush();
        mvc.perform(get("/api/time-entries").param("teamId", team.getId().toString()).with(user(memberPrincipal)))
                .andExpect(jsonPath("$.content[0].jiraIssueKey").value("PROJ-1")).andExpect(jsonPath("$.content[0].jiraIssueId").value("10001"));
    }

    @Test
    void refusesALinkTheClientCannotUse() throws Exception {
        send(post("/api/time-entries"), memberPrincipal, manualBody("abc")).andExpect(status().isBadRequest());
        send(post("/api/time-entries"), memberPrincipal, manualBody("1".repeat(21))).andExpect(status().isBadRequest());
        send(post("/api/time-entries"), memberPrincipal, manualBody("99999")).andExpect(status().isNotFound());
        ATLASSIAN.issueFailure = 403;
        send(post("/api/time-entries"), memberPrincipal, manualBody("10001")).andExpect(status().isNotFound());
        ATLASSIAN.issueFailure = 500;
        send(post("/api/time-entries"), memberPrincipal, manualBody("10001")).andExpect(status().isBadGateway());
        send(post("/api/time-entries"), unconnectedPrincipal, manualBody("10001")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from time_entries", Integer.class)).isZero();
    }

    @Test
    void jiraRateLimitStopsLinkingWithoutLosingTheEntryForm() throws Exception {
        ATLASSIAN.issueFailure = 429;
        send(post("/api/time-entries"), memberPrincipal, manualBody("10001")).andExpect(status().isTooManyRequests());
        ATLASSIAN.issueFailure = 0;
        send(post("/api/time-entries"), memberPrincipal, manualBody(null)).andExpect(status().isCreated());
    }

    @Test
    void anotherTeamsConnectionCannotBeUsedForAnEntry() throws Exception {
        var otherTeam = fixtures.team("Other", "UTC");
        var otherProject = fixtures.project(otherTeam, "Elsewhere", null);
        fixtures.member(otherTeam, member, TeamRole.MEMBER, Instant.now());
        var body = "{\"teamId\":\"" + otherTeam.getId() + "\",\"projectId\":\"" + otherProject.getId() + "\",\"workDate\":\"" + LocalDate.now()
                + "\",\"durationSeconds\":600,\"jiraIssueId\":\"10001\"}";
        send(post("/api/time-entries"), memberPrincipal, body).andExpect(status().isConflict());
    }

    @Test
    void editingKeepsReplacesOrRemovesTheLink() throws Exception {
        var id = createManual(memberPrincipal, "10001");
        assertThat(ATLASSIAN.issueCalls.get()).isEqualTo(1);

        send(patch("/api/time-entries/" + id), memberPrincipal, updateBody("10001")).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Edited")).andExpect(jsonPath("$.jiraIssueKey").value("PROJ-1"));
        assertThat(ATLASSIAN.issueCalls.get()).isEqualTo(1);

        send(patch("/api/time-entries/" + id), memberPrincipal, updateBody("10002")).andExpect(status().isOk())
                .andExpect(jsonPath("$.jiraIssueKey").value("PROJ-2"));
        assertThat(ATLASSIAN.issueCalls.get()).isEqualTo(2);

        send(patch("/api/time-entries/" + id), memberPrincipal, updateBody(null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.jiraIssueKey").doesNotExist());
        assertThat(jdbc.queryForMap("select jira_connection_id, jira_issue_id, jira_issue_key from time_entries where id = ?::uuid", id).values())
                .containsOnlyNulls();
    }

    @Test
    void keepingALinkStillWorksWhenThePersonDisconnectedTheirAccountMeanwhile() throws Exception {
        var id = createManual(memberPrincipal, "10001");
        jdbc.update("delete from jira_user_authorizations where user_id = ?", member.getId());
        send(patch("/api/time-entries/" + id), memberPrincipal, updateBody("10001")).andExpect(status().isOk())
                .andExpect(jsonPath("$.jiraIssueKey").value("PROJ-1"));
        send(patch("/api/time-entries/" + id), memberPrincipal, updateBody("10002")).andExpect(status().isConflict());
    }

    @Test
    void onlyTheOwnerOfAnEntryCanChangeItsLink() throws Exception {
        var id = createManual(memberPrincipal, "10001");
        send(patch("/api/time-entries/" + id), ownerPrincipal, updateBody("10002")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select jira_issue_key from time_entries where id = ?::uuid", String.class, id)).isEqualTo("PROJ-1");
    }

    @Test
    void theTeamReportCsvAndAccountExportShowTheKey() throws Exception {
        createManual(memberPrincipal, "10001");
        entityManager.flush();
        mvc.perform(get("/api/teams/" + team.getId() + "/reports/entries").param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString()).with(user(ownerPrincipal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].jiraIssueKey").value("PROJ-1"));
        var csv = new String(mvc.perform(get("/api/teams/" + team.getId() + "/reports/export.csv")
                        .param("from", LocalDate.now().toString()).param("to", LocalDate.now().toString()).with(user(ownerPrincipal)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("Zadanie Jira").contains(";Website;PROJ-1;Work;");
        mvc.perform(get("/api/teams/" + team.getId() + "/reports/export.pdf").param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString()).with(user(ownerPrincipal)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/account/export").with(user(memberPrincipal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timeEntries[0].jiraIssueKey").value("PROJ-1"));
    }

    @Test
    void disconnectingTheSiteRemovesTheLinksButKeepsTheTime() throws Exception {
        createManual(memberPrincipal, "10001");
        entityManager.flush();
        mvc.perform(delete("/api/integrations/jira/site").param("teamId", team.getId().toString()).with(user(ownerPrincipal)).with(csrf()))
                .andExpect(status().isNoContent());
        entityManager.clear();
        var row = jdbc.queryForMap("select duration_seconds, jira_issue_key from time_entries where user_id = ?", member.getId());
        assertThat(row.get("duration_seconds")).isEqualTo(600);
        assertThat(row.get("jira_issue_key")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from jira_connections", Integer.class)).isZero();
    }

    @Test
    void deletingTheTeamWithLinkedEntriesWorks() throws Exception {
        createManual(memberPrincipal, "10001");
        entityManager.flush();
        mvc.perform(delete("/api/teams/" + team.getId()).with(user(ownerPrincipal)).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from time_entries", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from jira_connections", Integer.class)).isZero();
    }

    @Test
    void theDatabaseRejectsHalfALink() throws Exception {
        var id = createManual(memberPrincipal, null);
        entityManager.flush();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("update time_entries set jira_issue_key = 'X-1' where id = ?::uuid", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsAConnectionOfAnotherTeam() throws Exception {
        var id = createManual(memberPrincipal, null);
        entityManager.flush();
        var otherTeam = fixtures.team("Other", "UTC");
        var otherConnection = connections.saveAndFlush(new JiraConnection(otherTeam, "cloud-2", "https://o.atlassian.net", "O", owner));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                        "update time_entries set jira_connection_id = ?, jira_issue_id = '1', jira_issue_key = 'X-1' where id = ?::uuid",
                        otherConnection.getId(), id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void historyOfOthersNeverLeaksAKeyToAnotherMember() throws Exception {
        createManual(memberPrincipal, "10001");
        entityManager.flush();
        mvc.perform(get("/api/time-entries").param("teamId", team.getId().toString()).with(user(ownerPrincipal)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("PROJ-1"))));
        assertThat(UUID.randomUUID()).isNotNull();
    }
}
