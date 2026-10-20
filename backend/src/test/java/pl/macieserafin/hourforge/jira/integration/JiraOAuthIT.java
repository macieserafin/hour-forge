package pl.macieserafin.hourforge.jira.integration;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class JiraOAuthIT extends JiraIntegrationTestSupport {
    private static final String BASE = "/api/integrations/jira";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;
    private Db db;

    private User owner;
    private User member;
    private User outsider;
    private Team team;
    private AppUserPrincipal ownerPrincipal;
    private AppUserPrincipal memberPrincipal;
    private MockHttpSession ownerSession;
    private MockHttpSession memberSession;

    /** JDBC sees only flushed rows and bypasses the persistence context, so it is synchronized around each use. */
    private final class Db {
        <T> T queryForObject(String sql, Class<T> type, Object... args) {
            entityManager.flush();
            return jdbcTemplate.queryForObject(sql, type, args);
        }

        <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
            entityManager.flush();
            return jdbcTemplate.queryForList(sql, type, args);
        }

        java.util.Map<String, Object> queryForMap(String sql, Object... args) {
            entityManager.flush();
            return jdbcTemplate.queryForMap(sql, args);
        }

        int update(String sql, Object... args) {
            entityManager.flush();
            int changed = jdbcTemplate.update(sql, args);
            entityManager.clear();
            return changed;
        }
    }

    @BeforeEach
    void setup() {
        db = new Db();
        owner = fixtures.user("owner@example.com", "Owner");
        member = fixtures.user("member@example.com", "Member");
        outsider = fixtures.user("outsider@example.com", "Outsider");
        team = fixtures.team("Studio", "Europe/Warsaw");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
        ownerPrincipal = new AppUserPrincipal(owner.getId(), owner.getEmail(), "unused");
        memberPrincipal = new AppUserPrincipal(member.getId(), member.getEmail(), "unused");
        ownerSession = new MockHttpSession();
        memberSession = new MockHttpSession();
    }

    @Test
    void statusSaysNothingIsConnectedYetAndOnlyMembersMayAsk() throws Exception {
        mvc.perform(get(BASE + "/status").param("teamId", team.getId().toString()).with(user(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.siteConnected").value(false))
                .andExpect(jsonPath("$.userConnected").value(false))
                .andExpect(jsonPath("$.canManage").value(true));
        mvc.perform(get(BASE + "/status").param("teamId", team.getId().toString()).with(user(memberPrincipal)))
                .andExpect(jsonPath("$.canManage").value(false));
        mvc.perform(get(BASE + "/status").param("teamId", team.getId().toString())
                        .with(user(new AppUserPrincipal(outsider.getId(), outsider.getEmail(), "unused"))))
                .andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/status").param("teamId", team.getId().toString())).andExpect(status().isUnauthorized());
    }

    @Test
    void theOwnerConnectsTheTeamSiteAndTokensAreStoredEncryptedAndNeverLogged(CapturedOutput output) throws Exception {
        var state = authorize(ownerPrincipal, ownerSession, "SITE");
        callback(ownerPrincipal, ownerSession, "good-code", state)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=connected&team=" + team.getId()))
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        assertThat(db.queryForObject("select count(*) from jira_connections where team_id = ? and cloud_id = 'cloud-1' and connected_by = ?",
                Integer.class, team.getId(), owner.getId())).isEqualTo(1);
        var stored = db.queryForMap("select encrypted_access_token, encrypted_refresh_token from jira_user_authorizations where user_id = ?",
                owner.getId());
        assertThat(stored.values().toString()).doesNotContain("access-1").doesNotContain("refresh-1").contains("v1:");
        assertThat(output.getAll()).doesNotContain("access-1").doesNotContain("refresh-1").doesNotContain("good-code")
                .doesNotContain(CLIENT_SECRET).doesNotContain(state);

        mvc.perform(get(BASE + "/status").param("teamId", team.getId().toString()).with(user(ownerPrincipal)))
                .andExpect(jsonPath("$.siteConnected").value(true))
                .andExpect(jsonPath("$.siteName").value("Team site"))
                .andExpect(jsonPath("$.userConnected").value(true));
    }

    @Test
    void theAuthorizationUrlCarriesTheConfiguredClientAndAStateThatIsOnlyStoredAsAHash() throws Exception {
        var url = mvc.perform(post(BASE + "/authorize").with(user(ownerPrincipal)).with(csrf()).session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON).content(authorizeBody("SITE")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var link = UriComponentsBuilder.fromUriString(JsonPath.<String>read(url, "$.url")).build();
        assertThat(link.getPath()).isEqualTo("/authorize");
        var params = link.getQueryParams();
        assertThat(params.getFirst("client_id")).isEqualTo("test-client-id");
        assertThat(params.getFirst("response_type")).isEqualTo("code");
        assertThat(params.getFirst("audience")).isEqualTo("api.atlassian.com");
        assertThat(params.getFirst("prompt")).isEqualTo("consent");
        assertThat(java.net.URLDecoder.decode(params.getFirst("scope"), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("read:jira-work offline_access");
        var raw = params.getFirst("state");
        assertThat(raw).hasSizeGreaterThanOrEqualTo(40);
        var hashes = db.queryForList("select state_hash from oauth_states where user_id = ?", String.class, owner.getId());
        assertThat(hashes).hasSize(1).doesNotContain(raw);
        assertThat(hashes.get(0)).hasSize(64);
    }

    @Test
    void anOwnerWithSeveralSitesChoosesOneAndTheOthersAreForgotten() throws Exception {
        ATLASSIAN.sites = List.of(new FakeAtlassian.Site("cloud-1", "https://one.atlassian.net", "One"),
                new FakeAtlassian.Site("cloud-2", "https://two.atlassian.net", "Two"));
        var state = authorize(ownerPrincipal, ownerSession, "SITE");
        callback(ownerPrincipal, ownerSession, "good-code", state)
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=choose-site&team=" + team.getId()));
        assertThat(db.queryForObject("select count(*) from jira_connections", Integer.class)).isZero();

        mvc.perform(get(BASE + "/sites").param("teamId", team.getId().toString()).with(user(ownerPrincipal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("Two"));
        mvc.perform(post(BASE + "/site").with(user(ownerPrincipal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"" + team.getId() + "\",\"cloudId\":\"cloud-3\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(BASE + "/site").with(user(ownerPrincipal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":\"" + team.getId() + "\",\"cloudId\":\"cloud-2\"}"))
                .andExpect(status().isNoContent());
        assertThat(db.queryForList("select cloud_id from jira_connections", String.class)).containsExactly("cloud-2");
        assertThat(db.queryForList("select cloud_id from jira_user_authorizations", String.class)).containsExactly("cloud-2");
    }

    @Test
    void membersCannotChooseTheSiteButCanConnectTheirOwnAccountToIt() throws Exception {
        connectOwner();
        mvc.perform(post(BASE + "/authorize").with(user(memberPrincipal)).with(csrf()).session(memberSession)
                        .contentType(MediaType.APPLICATION_JSON).content(authorizeBody("SITE")))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/sites").param("teamId", team.getId().toString()).with(user(memberPrincipal)))
                .andExpect(status().isForbidden());

        var state = authorize(memberPrincipal, memberSession, "MEMBER");
        callback(memberPrincipal, memberSession, "good-code-member", state)
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=connected&team=" + team.getId()));
        assertThat(db.queryForObject("select count(*) from jira_user_authorizations where user_id = ?", Integer.class, member.getId()))
                .isEqualTo(1);
        mvc.perform(get(BASE + "/status").param("teamId", team.getId().toString()).with(user(memberPrincipal)))
                .andExpect(jsonPath("$.userConnected").value(true)).andExpect(jsonPath("$.canManage").value(false));
    }

    @Test
    void aMemberWhoseJiraAccountCannotSeeTheTeamSiteIsToldSo() throws Exception {
        connectOwner();
        ATLASSIAN.sites = List.of(new FakeAtlassian.Site("cloud-9", "https://other.atlassian.net", "Other"));
        var state = authorize(memberPrincipal, memberSession, "MEMBER");
        callback(memberPrincipal, memberSession, "good-code-member", state)
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=no-access&team=" + team.getId()));
        assertThat(db.queryForObject("select count(*) from jira_user_authorizations where user_id = ?", Integer.class, member.getId()))
                .isZero();
    }

    @Test
    void connectingAMemberNeedsAChosenSiteAndMembership() throws Exception {
        mvc.perform(post(BASE + "/authorize").with(user(memberPrincipal)).with(csrf()).session(memberSession)
                        .contentType(MediaType.APPLICATION_JSON).content(authorizeBody("MEMBER")))
                .andExpect(status().isConflict());
        mvc.perform(post(BASE + "/authorize").with(user(new AppUserPrincipal(outsider.getId(), outsider.getEmail(), "unused")))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(authorizeBody("SITE")))
                .andExpect(status().isNotFound());
        mvc.perform(post(BASE + "/authorize").with(user(ownerPrincipal)).contentType(MediaType.APPLICATION_JSON)
                        .content(authorizeBody("SITE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aStateWorksOnceOnlyForItsOwnPersonAndSession() throws Exception {
        var state = authorize(ownerPrincipal, ownerSession, "SITE");
        callback(ownerPrincipal, new MockHttpSession(), "good-code", state)
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=invalid-state"));
        callback(memberPrincipal, memberSession, "good-code", state)
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=invalid-state"));
        assertThat(ATLASSIAN.requests()).isEmpty();

        callback(ownerPrincipal, ownerSession, "good-code", state).andExpect(header().string("Location", startsWith("http://localhost:5173/integrations?jira=connected")));
        callback(ownerPrincipal, ownerSession, "good-code", state)
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=invalid-state"));
        assertThat(ATLASSIAN.requests().stream().filter("POST /oauth/token"::equals).count()).isEqualTo(1);
    }

    @Test
    void forgedExpiredAndMissingStatesAreRefusedWithoutCallingAtlassian() throws Exception {
        callback(ownerPrincipal, ownerSession, "good-code", "forged-state-value")
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=invalid-state"));
        mvc.perform(get(BASE + "/callback").param("code", "good-code").with(user(ownerPrincipal)).session(ownerSession))
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=invalid-state"));
        mvc.perform(get(BASE + "/callback").param("code", "good-code").param("state", "x".repeat(500)).with(user(ownerPrincipal)).session(ownerSession))
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=invalid-state"));

        var state = authorize(ownerPrincipal, ownerSession, "SITE");
        db.update("update oauth_states set expires_at = now() - interval '1 minute' where user_id = ?", owner.getId());
        callback(ownerPrincipal, ownerSession, "good-code", state)
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=invalid-state"));
        assertThat(ATLASSIAN.requests()).isEmpty();
        mvc.perform(get(BASE + "/callback").param("code", "good-code").param("state", state)).andExpect(status().isUnauthorized());
    }

    @Test
    void aRefusedConsentOrCodeOrAnAtlassianOutageEndsInAFixedResultAndSpendsTheState() throws Exception {
        var denied = authorize(ownerPrincipal, ownerSession, "SITE");
        mvc.perform(get(BASE + "/callback").param("error", "access_denied").param("state", denied)
                        .with(user(ownerPrincipal)).session(ownerSession))
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=denied&team=" + team.getId()));
        callback(ownerPrincipal, ownerSession, "good-code", denied)
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=invalid-state"));

        var badCode = authorize(ownerPrincipal, ownerSession, "SITE");
        callback(ownerPrincipal, ownerSession, "bad-code", badCode)
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=denied&team=" + team.getId()));

        ATLASSIAN.tokenEndpointFailure = 500;
        var outage = authorize(ownerPrincipal, ownerSession, "SITE");
        callback(ownerPrincipal, ownerSession, "good-code", outage)
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=unavailable&team=" + team.getId()));
        assertThat(db.queryForObject("select count(*) from jira_connections", Integer.class)).isZero();
    }

    @Test
    void anOwnerWhoLostTheRoleBeforeTheCallbackCannotSetTheSite() throws Exception {
        var state = authorize(ownerPrincipal, ownerSession, "SITE");
        db.update("update team_members set role = 'MEMBER' where user_id = ?", owner.getId());
        callback(ownerPrincipal, ownerSession, "good-code", state)
                .andExpect(redirectedUrl("http://localhost:5173/integrations?jira=forbidden&team=" + team.getId()));
        assertThat(db.queryForObject("select count(*) from jira_connections", Integer.class)).isZero();
    }

    @Test
    void disconnectingAMemberKeepsTheSiteAndDisconnectingTheSiteRemovesEveryGrantForIt() throws Exception {
        connectOwner();
        var state = authorize(memberPrincipal, memberSession, "MEMBER");
        callback(memberPrincipal, memberSession, "good-code-member", state);

        mvc.perform(delete(BASE + "/connection").param("teamId", team.getId().toString()).with(user(memberPrincipal)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(db.queryForObject("select count(*) from jira_user_authorizations where user_id = ?", Integer.class, member.getId())).isZero();
        assertThat(db.queryForObject("select count(*) from jira_connections", Integer.class)).isEqualTo(1);

        mvc.perform(delete(BASE + "/site").param("teamId", team.getId().toString()).with(user(memberPrincipal)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/site").param("teamId", team.getId().toString()).with(user(ownerPrincipal)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(db.queryForObject("select count(*) from jira_connections", Integer.class)).isZero();
        assertThat(db.queryForObject("select count(*) from jira_user_authorizations", Integer.class)).isZero();
    }

    @Test
    void deletingTheTeamOrTheAccountRemovesTheJiraData() throws Exception {
        connectOwner();
        var state = authorize(memberPrincipal, memberSession, "MEMBER");
        callback(memberPrincipal, memberSession, "good-code-member", state);
        authorize(memberPrincipal, memberSession, "MEMBER");

        mvc.perform(delete("/api/teams/" + team.getId()).with(user(ownerPrincipal)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(db.queryForObject("select count(*) from jira_connections", Integer.class)).isZero();
        assertThat(db.queryForObject("select count(*) from jira_user_authorizations where cloud_id = 'cloud-1'", Integer.class)).isZero();
        assertThat(db.queryForObject("select count(*) from oauth_states where team_id = ?", Integer.class, team.getId())).isZero();
    }

    @Test
    void authorizingNeedsCsrfAndIsRateLimited() throws Exception {
        mvc.perform(post(BASE + "/authorize").with(user(ownerPrincipal)).contentType(MediaType.APPLICATION_JSON)
                        .content(authorizeBody("SITE")))
                .andExpect(status().isForbidden());
        for (int i = 0; i < 20; i++) authorize(ownerPrincipal, ownerSession, "SITE");
        mvc.perform(post(BASE + "/authorize").with(user(ownerPrincipal)).with(csrf()).session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON).content(authorizeBody("SITE")))
                .andExpect(status().isTooManyRequests());
    }

    private void connectOwner() throws Exception {
        callback(ownerPrincipal, ownerSession, "good-code", authorize(ownerPrincipal, ownerSession, "SITE"));
    }

    private String authorizeBody(String intent) {
        return "{\"teamId\":\"" + team.getId() + "\",\"intent\":\"" + intent + "\"}";
    }

    private String authorize(AppUserPrincipal principal, MockHttpSession session, String intent) throws Exception {
        var body = mvc.perform(post(BASE + "/authorize").with(user(principal)).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(authorizeBody(intent)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UriComponentsBuilder.fromUriString(JsonPath.<String>read(body, "$.url")).build().getQueryParams().getFirst("state");
    }

    private ResultActions callback(AppUserPrincipal principal, MockHttpSession session, String code, String state) throws Exception {
        return mvc.perform(get(BASE + "/callback").param("code", code).param("state", state).with(user(principal)).session(session));
    }
}
