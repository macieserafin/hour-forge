package pl.macieserafin.hourforge.jobs.integration;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.invitation.TeamInvite;
import pl.macieserafin.hourforge.invitation.TeamInviteRepository;
import pl.macieserafin.hourforge.jobs.RetentionService;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionIT extends ApiIntegrationTestSupport {
    @Autowired RetentionService retention;
    @Autowired UserRepository users;
    @Autowired UserCredentialRepository credentials;
    @Autowired TeamInviteRepository invites;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    private final Instant now = Instant.parse("2026-10-21T03:30:00Z");

    @BeforeEach
    void clean() {
        // The shared test database may hold data of other tests; each test only looks at its own rows.
        entityManager.flush();
    }

    private User unconfirmed(String email, int daysOld) {
        var user = users.saveAndFlush(new User(email, "Unconfirmed"));
        credentials.saveAndFlush(new UserCredential(user, "x".repeat(60)));
        jdbc.update("update users set created_at = ? where id = ?", java.sql.Timestamp.from(now.minus(daysOld, ChronoUnit.DAYS)), user.getId());
        token(user, "RESET_PASSWORD", now.plus(1, ChronoUnit.HOURS), null);
        entityManager.clear();
        return user;
    }

    private void token(User user, String purpose, Instant expiresAt, Instant usedAt) {
        jdbc.update("insert into email_tokens (id, user_id, purpose, token_hash, expires_at, used_at) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), user.getId(), purpose, UUID.randomUUID().toString().replace("-", "").repeat(2),
                java.sql.Timestamp.from(expiresAt), usedAt == null ? null : java.sql.Timestamp.from(usedAt));
    }

    private boolean exists(User user) {
        entityManager.flush();
        return jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, user.getId()) == 1;
    }

    @Test
    void removesAccountsNobodyConfirmedInTimeTogetherWithTheirTokens() {
        var old = unconfirmed("old@example.com", 8);
        var recent = unconfirmed("recent@example.com", 6);
        var verified = fixtures.user("verified@example.com", "Verified");
        jdbc.update("update users set created_at = ? where id = ?", java.sql.Timestamp.from(now.minus(400, ChronoUnit.DAYS)), verified.getId());

        var purged = retention.purge(now);
        entityManager.clear();

        assertThat(purged.accounts()).isGreaterThanOrEqualTo(1);
        assertThat(exists(old)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from email_tokens where user_id = ?", Integer.class, old.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_credentials where user_id = ?", Integer.class, old.getId())).isZero();
        assertThat(exists(recent)).isTrue();
        assertThat(exists(verified)).isTrue();
    }

    @Test
    void neverRemovesAnUnconfirmedAccountThatOwnsData() {
        var old = unconfirmed("owner@example.com", 30);
        var team = fixtures.team("Kept", "UTC");
        fixtures.member(team, old, TeamRole.OWNER, now.minus(29, ChronoUnit.DAYS));
        var inviter = unconfirmed("inviter@example.com", 30);
        invites.saveAndFlush(new TeamInvite(team, inviter, "i".repeat(64), 1, now.plus(1, ChronoUnit.DAYS)));

        retention.purge(now);
        entityManager.clear();

        assertThat(exists(old)).isTrue();
        assertThat(exists(inviter)).isTrue();
    }

    @Test
    void sweepsOnlySpentOneTimeTokensAndOAuthStatesByAge() {
        var user = fixtures.user("tokens@example.com", "Tokens");
        token(user, "RESET_PASSWORD", now.minus(8, ChronoUnit.DAYS), null);
        token(user, "VERIFY_EMAIL", now.plus(1, ChronoUnit.DAYS), now.minus(8, ChronoUnit.DAYS));
        token(user, "VERIFY_EMAIL", now.minus(2, ChronoUnit.DAYS), null);
        token(user, "RESET_PASSWORD", now.plus(1, ChronoUnit.DAYS), null);
        var team = fixtures.team("States", "UTC");
        jdbc.update("insert into oauth_states (id, state_hash, session_hash, user_id, team_id, intent, expires_at) values (?, ?, ?, ?, ?, 'SITE', ?)",
                UUID.randomUUID(), "a".repeat(64), "b".repeat(64), user.getId(), team.getId(), java.sql.Timestamp.from(now.minus(8, ChronoUnit.DAYS)));
        jdbc.update("insert into oauth_states (id, state_hash, session_hash, user_id, team_id, intent, expires_at) values (?, ?, ?, ?, ?, 'SITE', ?)",
                UUID.randomUUID(), "c".repeat(64), "b".repeat(64), user.getId(), team.getId(), java.sql.Timestamp.from(now.plus(5, ChronoUnit.MINUTES)));

        retention.purge(now);
        entityManager.clear();

        assertThat(jdbc.queryForObject("select count(*) from email_tokens where user_id = ?", Integer.class, user.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from oauth_states where user_id = ?", Integer.class, user.getId())).isEqualTo(1);
    }

    @Test
    void sweepsInvitationsOnlyLongAfterTheyExpiredOrWereRevoked() {
        var owner = fixtures.user("invites@example.com", "Owner");
        var team = fixtures.team("Invites", "UTC");
        var longExpired = invites.saveAndFlush(new TeamInvite(team, owner, "1".repeat(64), 1, now.minus(100, ChronoUnit.DAYS)));
        var recentlyExpired = invites.saveAndFlush(new TeamInvite(team, owner, "2".repeat(64), 1, now.minus(10, ChronoUnit.DAYS)));
        var longRevoked = invites.saveAndFlush(new TeamInvite(team, owner, "3".repeat(64), 1, now.plus(10, ChronoUnit.DAYS)));
        var recentlyRevoked = invites.saveAndFlush(new TeamInvite(team, owner, "4".repeat(64), 1, now.plus(10, ChronoUnit.DAYS)));
        var active = invites.saveAndFlush(new TeamInvite(team, owner, "5".repeat(64), 1, now.plus(10, ChronoUnit.DAYS)));
        jdbc.update("update team_invites set revoked_at = ? where id = ?", java.sql.Timestamp.from(now.minus(100, ChronoUnit.DAYS)), longRevoked.getId());
        jdbc.update("update team_invites set revoked_at = ? where id = ?", java.sql.Timestamp.from(now.minus(10, ChronoUnit.DAYS)), recentlyRevoked.getId());
        entityManager.clear();

        retention.purge(now);
        entityManager.clear();

        var left = jdbc.queryForList("select id from team_invites where team_id = ?", UUID.class, team.getId());
        assertThat(left).containsExactlyInAnyOrder(recentlyExpired.getId(), recentlyRevoked.getId(), active.getId())
                .doesNotContain(longExpired.getId(), longRevoked.getId());
    }

    @Test
    void runningTwiceChangesNothingTheSecondTime() {
        unconfirmed("twice@example.com", 20);
        retention.purge(now);
        var second = retention.purge(now);
        assertThat(second.accounts()).isZero();
        assertThat(second.invitations()).isGreaterThanOrEqualTo(0);
    }
}
