package pl.macieserafin.hourforge.jira.integration;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.macieserafin.hourforge.jira.JiraApiClient;
import pl.macieserafin.hourforge.jira.JiraAtlassianClient;
import pl.macieserafin.hourforge.jira.JiraConnection;
import pl.macieserafin.hourforge.jira.JiraConnectionRepository;
import pl.macieserafin.hourforge.jira.JiraSearchCache;
import pl.macieserafin.hourforge.jira.JiraTokenService;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JiraIssueIT extends JiraIntegrationTestSupport {
    private static final String ISSUES = "/api/integrations/jira/issues";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired JiraConnectionRepository connections;
    @Autowired JiraTokenService tokens;
    @Autowired JiraAtlassianClient atlassian;
    @Autowired JiraSearchCache cache;

    private User member;
    private User other;
    private User outsider;
    private Team team;
    private AppUserPrincipal memberPrincipal;
    private AppUserPrincipal otherPrincipal;

    @BeforeEach
    void setup() {
        cache.clear();
        var owner = fixtures.user("owner@example.com", "Owner");
        member = fixtures.user("member@example.com", "Member");
        other = fixtures.user("other@example.com", "Other");
        outsider = fixtures.user("outsider@example.com", "Outsider");
        team = fixtures.team("Studio", "Europe/Warsaw");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
        fixtures.member(team, other, TeamRole.MEMBER, Instant.now());
        connections.saveAndFlush(new JiraConnection(team, "cloud-1", "https://team.atlassian.net", "Team site", owner));
        memberPrincipal = new AppUserPrincipal(member.getId(), member.getEmail(), "unused");
        otherPrincipal = new AppUserPrincipal(other.getId(), other.getEmail(), "unused");
        connect(member);
    }

    private void connect(User person) {
        tokens.store(person, "cloud-1", atlassian.exchangeCode("good-code"));
    }

    private ResultActions search(AppUserPrincipal principal, String... params) throws Exception {
        var request = get(ISSUES).param("teamId", team.getId().toString()).with(user(principal));
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        entityManager.flush();
        return mvc.perform(request);
    }

    @Test
    void listsTheIssuesOfThePersonWithOnlyTheFieldsTheAppShows() throws Exception {
        search(memberPrincipal).andExpect(status().isOk())
                .andExpect(jsonPath("$.issues.length()").value(2))
                .andExpect(jsonPath("$.issues[0].key").value("PROJ-1"))
                .andExpect(jsonPath("$.issues[0].summary").value("First task"))
                .andExpect(jsonPath("$.issues[0].status").value("To Do"))
                .andExpect(jsonPath("$.issues[0].issueType").value("Task"))
                .andExpect(jsonPath("$.issues[0].projectKey").value("PROJ"))
                .andExpect(jsonPath("$.issues[0].id").value("10001"))
                .andExpect(jsonPath("$.nextPageToken").doesNotExist());
        var sent = ATLASSIAN.searchBodies().get(0);
        assertThat(sent).contains("assignee = currentUser() AND statusCategory != Done ORDER BY updated DESC");
        assertThat(ATLASSIAN.requests()).contains("POST /ex/jira/cloud-1/rest/api/3/search/jql");
    }

    @Test
    void pagesThroughResultsWithTheOpaqueToken() throws Exception {
        var many = new ArrayList<FakeAtlassian.Issue>();
        for (int i = 1; i <= 45; i++) many.add(new FakeAtlassian.Issue(String.valueOf(20000 + i), "PROJ-" + i, "Task " + i));
        ATLASSIAN.issues = many;
        search(memberPrincipal).andExpect(jsonPath("$.issues.length()").value(20)).andExpect(jsonPath("$.nextPageToken").value("page-20"));
        search(memberPrincipal, "pageToken", "page-20").andExpect(jsonPath("$.issues.length()").value(20))
                .andExpect(jsonPath("$.issues[0].key").value("PROJ-21")).andExpect(jsonPath("$.nextPageToken").value("page-40"));
        search(memberPrincipal, "pageToken", "page-40").andExpect(jsonPath("$.issues.length()").value(5))
                .andExpect(jsonPath("$.nextPageToken").doesNotExist());
    }

    @Test
    void filtersBecomeFixedJqlAndHostileTextNeverReachesJiraAsSyntax() throws Exception {
        search(memberPrincipal, "assignee", "ANYONE", "sprint", "ACTIVE", "includeDone", "true", "project", "PROJ", "q", "x\" OR project is not EMPTY --")
                .andExpect(status().isOk());
        var sent = ATLASSIAN.searchBodies().get(0);
        assertThat(sent).contains("project = \\\"PROJ\\\"").contains("sprint in openSprints()").doesNotContain("currentUser")
                .doesNotContain("statusCategory").contains("text ~ \\\"x* OR* project* is* not* EMPTY* --*\\\"");
    }

    @Test
    void refusesBadFilterValues() throws Exception {
        search(memberPrincipal, "project", "bad key\"").andExpect(status().isBadRequest());
        search(memberPrincipal, "sprint", "EVERYTHING").andExpect(status().isBadRequest());
        search(memberPrincipal, "assignee", "NOBODY").andExpect(status().isBadRequest());
        search(memberPrincipal, "pageToken", "x".repeat(2001)).andExpect(status().isBadRequest());
        assertThat(ATLASSIAN.searchCalls.get()).isZero();
    }

    @Test
    void needsMembershipASiteAndTheCallersOwnConnectedAccount() throws Exception {
        search(otherPrincipal).andExpect(status().isConflict());
        search(new AppUserPrincipal(outsider.getId(), outsider.getEmail(), "unused")).andExpect(status().isNotFound());
        mvc.perform(get(ISSUES).param("teamId", team.getId().toString())).andExpect(status().isUnauthorized());
        connections.deleteAllByTeamId(team.getId());
        entityManager.clear();
        search(memberPrincipal).andExpect(status().isConflict());
        assertThat(ATLASSIAN.searchCalls.get()).isZero();
    }

    @Test
    void repeatedSearchesAreServedFromACacheThatIsPerPerson() throws Exception {
        search(memberPrincipal).andExpect(status().isOk());
        search(memberPrincipal).andExpect(status().isOk());
        assertThat(ATLASSIAN.searchCalls.get()).isEqualTo(1);
        search(memberPrincipal, "q", "task").andExpect(status().isOk());
        assertThat(ATLASSIAN.searchCalls.get()).isEqualTo(2);

        connect(other);
        search(otherPrincipal).andExpect(status().isOk());
        assertThat(ATLASSIAN.searchCalls.get()).isEqualTo(3);
        // The second person's request used the second person's own token.
        assertThat(ATLASSIAN.requests().stream().filter(request -> request.contains("search")).count()).isEqualTo(3);
    }

    @Test
    void disconnectingForgetsTheCachedResults() throws Exception {
        search(memberPrincipal).andExpect(status().isOk());
        mvc.perform(delete("/api/integrations/jira/connection").param("teamId", team.getId().toString()).with(user(memberPrincipal)).with(csrf()))
                .andExpect(status().isNoContent());
        entityManager.clear();
        search(memberPrincipal).andExpect(status().isConflict());
    }

    @Test
    void waitsOutATooManyRequestsAnswerWithoutCallingJiraAgain() throws Exception {
        ATLASSIAN.searchFailure = 429;
        search(memberPrincipal).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "3"));
        ATLASSIAN.searchFailure = 0;
        search(memberPrincipal).andExpect(status().isTooManyRequests());
        assertThat(ATLASSIAN.searchCalls.get()).isEqualTo(1);
    }

    @Test
    void translatesJiraErrorsAndForgetsATokenJiraNoLongerAccepts() throws Exception {
        ATLASSIAN.searchFailure = 400;
        search(memberPrincipal).andExpect(status().isBadRequest());
        ATLASSIAN.searchFailure = 403;
        search(memberPrincipal).andExpect(status().isForbidden());
        ATLASSIAN.searchFailure = 500;
        search(memberPrincipal).andExpect(status().isBadGateway());
        assertThat(count("select count(*) from jira_user_authorizations where user_id = ?", member)).isEqualTo(1);

        ATLASSIAN.revokeAccessTokens();
        search(memberPrincipal).andExpect(status().isConflict());
        assertThat(count("select count(*) from jira_user_authorizations where user_id = ?", member)).isZero();
    }

    @Test
    void theCloudIdIsCheckedBeforeItBecomesPartOfAnAddress() {
        var api = new JiraApiClient(new pl.macieserafin.hourforge.jira.JiraProperties(true, "id", "secret", "http://localhost/cb", null,
                null, null, null, null, null));
        for (var bad : List.of("../admin", "a/b", "a?b", "", "x".repeat(65), "a b")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> api.search(bad, "token", "assignee = currentUser()", null, 20))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private int count(String sql, User person) {
        entityManager.flush();
        return jdbc.queryForObject(sql, Integer.class, person.getId());
    }
}
