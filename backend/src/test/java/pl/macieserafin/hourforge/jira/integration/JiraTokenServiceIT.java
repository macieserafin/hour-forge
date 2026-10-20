package pl.macieserafin.hourforge.jira.integration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pl.macieserafin.hourforge.jira.JiraUserAuthorization;
import pl.macieserafin.hourforge.jira.JiraUserAuthorizationRepository;
import pl.macieserafin.hourforge.jira.TokenCipher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.jira.JiraAtlassianClient;
import pl.macieserafin.hourforge.jira.JiraTokenService;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Not wrapped in a transaction: the refresh lock and the concurrent callers need real commits. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(OutputCaptureExtension.class)
class JiraTokenServiceIT extends JiraIntegrationTestSupport {
    @Autowired JiraTokenService tokens;
    @Autowired JiraAtlassianClient atlassian;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired JiraUserAuthorizationRepository authorizations;

    private User user;

    @BeforeEach
    void createUser() {
        user = fixtures.user("tokens-" + UUID.randomUUID() + "@example.com", "Tokens");
    }

    @AfterEach
    void removeData() {
        jdbc.update("delete from jira_user_authorizations where user_id = ?", user.getId());
        jdbc.update("delete from users where id = ?", user.getId());
    }

    @Test
    void aFreshTokenIsReusedAndAnExpiredOneIsRefreshedOnce() {
        ATLASSIAN.expiresInSeconds = 1;
        tokens.store(user, "cloud-1", atlassian.exchangeCode("good-code"));
        ATLASSIAN.expiresInSeconds = 3600;

        assertThat(tokens.accessToken(user.getId(), "cloud-1")).isEqualTo("access-2");
        assertThat(tokens.accessToken(user.getId(), "cloud-1")).isEqualTo("access-2");
        assertThat(ATLASSIAN.refreshCalls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select encrypted_access_token from jira_user_authorizations where user_id = ?", String.class,
                user.getId())).doesNotContain("access-2");
    }

    @Test
    void concurrentCallersSpendTheRotatingRefreshTokenOnlyOnce() throws Exception {
        ATLASSIAN.expiresInSeconds = 1;
        tokens.store(user, "cloud-1", atlassian.exchangeCode("good-code"));
        ATLASSIAN.expiresInSeconds = 3600;
        ATLASSIAN.refreshDelayMillis = 300;

        var pool = Executors.newFixedThreadPool(5);
        try {
            var calls = new ArrayList<Callable<String>>();
            for (int i = 0; i < 5; i++) calls.add(() -> tokens.accessToken(user.getId(), "cloud-1"));
            for (var result : pool.invokeAll(calls)) assertThat(result.get()).isEqualTo("access-2");
        } finally {
            pool.shutdownNow();
        }
        assertThat(ATLASSIAN.refreshCalls.get()).isEqualTo(1);
    }

    @Test
    void oneGrantSharedByTwoSitesStaysInSyncAfterARefresh() {
        ATLASSIAN.expiresInSeconds = 1;
        var granted = atlassian.exchangeCode("good-code");
        tokens.store(user, "cloud-1", granted);
        tokens.store(user, "cloud-2", granted);
        ATLASSIAN.expiresInSeconds = 3600;

        assertThat(tokens.accessToken(user.getId(), "cloud-1")).isEqualTo("access-2");
        assertThat(tokens.accessToken(user.getId(), "cloud-2")).isEqualTo("access-2");
        assertThat(ATLASSIAN.refreshCalls.get()).isEqualTo(1);
    }

    @Test
    void aRefusedRefreshTokenDisconnectsThePerson() {
        ATLASSIAN.expiresInSeconds = 1;
        tokens.store(user, "cloud-1", atlassian.exchangeCode("good-code"));
        ATLASSIAN.reset();

        assertThatThrownBy(() -> tokens.accessToken(user.getId(), "cloud-1")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not connected");
        assertThat(jdbc.queryForObject("select count(*) from jira_user_authorizations where user_id = ?", Integer.class, user.getId())).isZero();
    }

    @Test
    void anAtlassianOutageKeepsTheConnectionAndReportsABadGateway() {
        ATLASSIAN.expiresInSeconds = 1;
        tokens.store(user, "cloud-1", atlassian.exchangeCode("good-code"));
        ATLASSIAN.tokenEndpointFailure = 503;

        assertThatThrownBy(() -> tokens.accessToken(user.getId(), "cloud-1")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not reachable");
        assertThat(jdbc.queryForObject("select count(*) from jira_user_authorizations where user_id = ?", Integer.class, user.getId())).isEqualTo(1);
    }

    @Test
    void aTokenThatCannotBeDecryptedMeansTheNeedToConnectAgain() {
        tokens.store(user, "cloud-1", atlassian.exchangeCode("good-code"));
        jdbc.update("update jira_user_authorizations set encrypted_access_token = 'v1:AAAA:AAAA' where user_id = ?", user.getId());

        assertThatThrownBy(() -> tokens.accessToken(user.getId(), "cloud-1")).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("select count(*) from jira_user_authorizations where user_id = ?", Integer.class, user.getId())).isZero();
    }

    @Test
    void tokensWrittenWithAnOlderKeyAreRewrittenWithTheActiveKeyWhenUsed() {
        var old = new TokenCipher("v0", OLD_KEY, Map.of());
        var context = user.getId() + ":cloud-1";
        authorizations.saveAndFlush(new JiraUserAuthorization(user, "cloud-1", old.encrypt("legacy-access", context), old.encrypt("legacy-refresh", context),
                Instant.now().plusSeconds(3600), "read:jira-work", "v0"));

        assertThat(tokens.accessToken(user.getId(), "cloud-1")).isEqualTo("legacy-access");

        var row = jdbc.queryForMap("select encrypted_access_token, encrypted_refresh_token, encryption_key_version from jira_user_authorizations where user_id = ?", user.getId());
        assertThat(row.get("encryption_key_version")).isEqualTo("v1");
        assertThat(row.get("encrypted_access_token").toString()).startsWith("v1:");
        assertThat(row.get("encrypted_refresh_token").toString()).startsWith("v1:");
        assertThat(tokens.accessToken(user.getId(), "cloud-1")).isEqualTo("legacy-access");
    }

    @Test
    void neitherTokensNorTheClientSecretReachTheLogsDuringRefreshOrFailure(CapturedOutput output) {
        ATLASSIAN.expiresInSeconds = 1;
        tokens.store(user, "cloud-1", atlassian.exchangeCode("good-code"));
        ATLASSIAN.expiresInSeconds = 3600;
        tokens.accessToken(user.getId(), "cloud-1");
        ATLASSIAN.tokenEndpointFailure = 500;
        assertThatThrownBy(() -> atlassian.exchangeCode("good-code")).isInstanceOf(JiraAtlassianClient.UpstreamException.class);
        assertThatThrownBy(() -> atlassian.exchangeCode("bad-code")).isInstanceOf(RuntimeException.class);

        assertThat(output.getAll()).doesNotContain("access-").doesNotContain("refresh-").doesNotContain("good-code").doesNotContain(CLIENT_SECRET);
    }

    @Test
    void aCiphertextCopiedToAnotherPersonDoesNotWork() {
        var other = fixtures.user("other-" + UUID.randomUUID() + "@example.com", "Other");
        try {
            tokens.store(user, "cloud-1", atlassian.exchangeCode("good-code"));
            tokens.store(other, "cloud-1", atlassian.exchangeCode("good-code"));
            jdbc.update("update jira_user_authorizations set encrypted_access_token = (select encrypted_access_token from jira_user_authorizations where user_id = ?) where user_id = ?",
                    user.getId(), other.getId());
            assertThatThrownBy(() -> tokens.accessToken(other.getId(), "cloud-1")).isInstanceOf(ResponseStatusException.class);
            assertThat(tokens.accessToken(user.getId(), "cloud-1")).isEqualTo("access-1");
        } finally {
            jdbc.update("delete from jira_user_authorizations where user_id = ?", other.getId());
            jdbc.update("delete from users where id = ?", other.getId());
        }
    }
}
