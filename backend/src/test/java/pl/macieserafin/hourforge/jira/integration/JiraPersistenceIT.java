package pl.macieserafin.hourforge.jira.integration;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.jira.JiraConnection;
import pl.macieserafin.hourforge.jira.JiraConnectionRepository;
import pl.macieserafin.hourforge.jira.JiraUserAuthorization;
import pl.macieserafin.hourforge.jira.JiraUserAuthorizationRepository;
import pl.macieserafin.hourforge.jira.OAuthState;
import pl.macieserafin.hourforge.jira.OAuthStateRepository;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class JiraPersistenceIT extends IntegrationTestSupport {
    @Autowired JiraConnectionRepository connections;
    @Autowired JiraUserAuthorizationRepository authorizations;
    @Autowired OAuthStateRepository states;
    @Autowired EntityManager entityManager;

    private final Instant now = Instant.parse("2026-10-20T10:00:00Z");

    @Test
    void aTeamHasAtMostOneJiraSite() {
        var owner = fixtures.user("owner@example.com", "Owner");
        var team = fixtures.team("Forge", "UTC");
        connections.saveAndFlush(new JiraConnection(team, "cloud-1", "https://a.atlassian.net", "A", owner));
        assertThatThrownBy(() -> connections.saveAndFlush(new JiraConnection(team, "cloud-2", "https://b.atlassian.net", "B", owner)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aPersonHasOneAuthorizationPerSite() {
        var user = fixtures.user("member@example.com", "Member");
        authorizations.saveAndFlush(new JiraUserAuthorization(user, "cloud-1", "v1:a:b", "v1:c:d", now.plusSeconds(3600), "read:jira-work", "v1"));
        authorizations.saveAndFlush(new JiraUserAuthorization(user, "cloud-2", "v1:a:b", null, now.plusSeconds(3600), "read:jira-work", "v1"));
        assertThatThrownBy(() -> authorizations.saveAndFlush(
                new JiraUserAuthorization(user, "cloud-1", "v1:x:y", null, now.plusSeconds(3600), "read:jira-work", "v1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aStateHashIsUnique() {
        var user = fixtures.user("member@example.com", "Member");
        var team = fixtures.team("Forge", "UTC");
        var hash = "a".repeat(64);
        states.saveAndFlush(new OAuthState(hash, "b".repeat(64), user, team.getId(), OAuthState.Intent.MEMBER, now.plusSeconds(600)));
        assertThatThrownBy(() -> states.saveAndFlush(
                new OAuthState(hash, "c".repeat(64), user, team.getId(), OAuthState.Intent.SITE, now.plusSeconds(600))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aShortStateHashIsRejected() {
        var user = fixtures.user("member@example.com", "Member");
        var team = fixtures.team("Forge", "UTC");
        assertThatThrownBy(() -> states.saveAndFlush(
                new OAuthState("short", "b".repeat(64), user, team.getId(), OAuthState.Intent.MEMBER, now.plusSeconds(600))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usedStatesAreFoundByHashAndExpiredOnesAreCleaned() {
        var user = fixtures.user("member@example.com", "Member");
        var team = fixtures.team("Forge", "UTC");
        var live = states.saveAndFlush(new OAuthState("1".repeat(64), "b".repeat(64), user, team.getId(), OAuthState.Intent.MEMBER, now.plusSeconds(600)));
        states.saveAndFlush(new OAuthState("2".repeat(64), "b".repeat(64), user, team.getId(), OAuthState.Intent.MEMBER, now.minusSeconds(600)));
        live.markUsed(now);
        states.flush();
        assertThat(states.deleteExpired(now)).isEqualTo(1);
        entityManager.clear();
        assertThat(states.findByStateHash("1".repeat(64)).orElseThrow().getUsedAt()).isEqualTo(now);
        assertThat(states.findByStateHash("2".repeat(64))).isEmpty();
    }
}
