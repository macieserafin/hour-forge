package pl.macieserafin.hourforge.jira.integration;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Negative cases around the Jira endpoints: missing sign-in or CSRF token, mixed-up teams, frozen teams, hostile redirects. */
class JiraSecurityIT extends JiraIntegrationTestSupport {
    private static final String BASE = "/api/integrations/jira";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired TeamRepository teams;

    private User owner;
    private Team teamA;
    private Team teamB;
    private AppUserPrincipal principal;

    @BeforeEach
    void setup() {
        owner = fixtures.user("owner@example.com", "Owner");
        teamA = fixtures.team("A", "UTC");
        teamB = fixtures.team("B", "UTC");
        fixtures.member(teamA, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(teamB, owner, TeamRole.OWNER, Instant.now());
        principal = new AppUserPrincipal(owner.getId(), owner.getEmail(), "unused");
    }

    private String body(Team team, String intent) {
        return "{\"teamId\":\"" + team.getId() + "\",\"intent\":\"" + intent + "\"}";
    }

    private String authorize(Team team, org.springframework.mock.web.MockHttpSession session) throws Exception {
        entityManager.flush();
        var response = mvc.perform(post(BASE + "/authorize").with(user(principal)).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body(team, "SITE")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UriComponentsBuilder.fromUriString(JsonPath.<String>read(response, "$.url")).build().getQueryParams().getFirst("state");
    }

    @Test
    void everyEndpointNeedsASignedInPerson() throws Exception {
        var team = teamA.getId().toString();
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get(BASE + "/status").param("teamId", team), get(BASE + "/sites").param("teamId", team),
                get(BASE + "/issues").param("teamId", team), get(BASE + "/callback").param("code", "x").param("state", "y"),
                post(BASE + "/authorize").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(teamA, "SITE")),
                post(BASE + "/site").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"teamId\":\"" + team + "\",\"cloudId\":\"c\"}"),
                delete(BASE + "/connection").param("teamId", team).with(csrf()), delete(BASE + "/site").param("teamId", team).with(csrf())}) {
            mvc.perform(request).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void everyChangeNeedsACsrfToken() throws Exception {
        var team = teamA.getId().toString();
        mvc.perform(post(BASE + "/site").with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + team + "\",\"cloudId\":\"c\"}")).andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/connection").param("teamId", team).with(user(principal))).andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/site").param("teamId", team).with(user(principal))).andExpect(status().isForbidden());
    }

    @Test
    void theCallbackAlwaysReturnsToTheConfiguredAppWhateverTheRequestSays() throws Exception {
        var session = new org.springframework.mock.web.MockHttpSession();
        var state = authorize(teamA, session);
        mvc.perform(get(BASE + "/callback").param("code", "good-code").param("state", state).param("redirect_uri", "https://evil.test/steal")
                        .param("returnTo", "https://evil.test").param("next", "//evil.test").with(user(principal)).session(session))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith("http://localhost:5173/integrations?jira=")));
    }

    @Test
    void aStateStartedForOneTeamOnlyConnectsThatTeam() throws Exception {
        var session = new org.springframework.mock.web.MockHttpSession();
        var state = authorize(teamA, session);
        mvc.perform(get(BASE + "/callback").param("code", "good-code").param("state", state).with(user(principal)).session(session))
                .andExpect(status().isFound());
        entityManager.flush();
        assertThat(jdbc.queryForList("select team_id from jira_connections", UUID.class)).containsExactly(teamA.getId());
    }

    @Test
    void anArchivedTeamKeepsItsJiraSiteFrozen() throws Exception {
        var session = new org.springframework.mock.web.MockHttpSession();
        callbackConnect(teamA, session);
        var team = teams.findById(teamA.getId()).orElseThrow();
        team.setArchivedAt(Instant.now());
        teams.saveAndFlush(team);

        mvc.perform(post(BASE + "/authorize").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(teamA, "SITE")))
                .andExpect(status().isConflict());
        mvc.perform(delete(BASE + "/site").param("teamId", teamA.getId().toString()).with(user(principal)).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post(BASE + "/site").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + teamA.getId() + "\",\"cloudId\":\"cloud-1\"}")).andExpect(status().isConflict());
        entityManager.flush();
        assertThat(jdbc.queryForObject("select count(*) from jira_connections", Integer.class)).isEqualTo(1);
    }

    private void callbackConnect(Team team, org.springframework.mock.web.MockHttpSession session) throws Exception {
        var state = authorize(team, session);
        mvc.perform(get(BASE + "/callback").param("code", "good-code").param("state", state).with(user(principal)).session(session))
                .andExpect(status().isFound());
        entityManager.flush();
    }
}
