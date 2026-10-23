package pl.macieserafin.hourforge.jobs.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.macieserafin.hourforge.jobs.InactiveAccountService;
import pl.macieserafin.hourforge.jobs.InactiveAccountService.Outcome;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InactiveAccountsIT extends ApiIntegrationTestSupport {
    private static final Duration AGE = Duration.ofDays(730);

    @Autowired InactiveAccountService inactive;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Test
    void warnsThirtyAndSevenDaysBeforeAndRemovesOnlyAfterTheLastWarning() {
        var person = unused("sleeping@example.test", AGE.minusDays(29));

        assertThat(inactive.due(now)).contains(person.getId());
        assertThat(handle(person, now)).isEqualTo(Outcome.WARNED);
        assertThat(mail.last().subject()).startsWith("Twoje konto w HourForge zostanie usunięte");
        assertThat(handle(person, now.plus(Duration.ofDays(1)))).isEqualTo(Outcome.NOTHING);

        assertThat(handle(person, now.plus(Duration.ofDays(23)))).isEqualTo(Outcome.WARNED);
        assertThat(mail.sent()).hasSize(2);
        // The last warning gave a date a week away: nothing happens before it.
        assertThat(handle(person, now.plus(Duration.ofDays(29)))).isEqualTo(Outcome.NOTHING);

        assertThat(handle(person, now.plus(Duration.ofDays(31)))).isEqualTo(Outcome.REMOVED);
        assertThat(reload(person).getDeletedAt()).isNotNull();
        assertThat(mail.last().subject()).isEqualTo("Konto w HourForge zostało usunięte");
    }

    @Test
    void usingTheAppCancelsTheWarnings() {
        var person = unused("back-again@example.test", AGE.minusDays(5));
        assertThat(handle(person, now)).isEqualTo(Outcome.WARNED);

        users.markActive(person.getId(), now.truncatedTo(ChronoUnit.DAYS));
        entityManager.clear();

        assertThat(reload(person).getInactivityWarnedDays()).isNull();
        assertThat(inactive.due(now)).doesNotContain(person.getId());
        assertThat(handle(person, now.plus(Duration.ofDays(30)))).isEqualTo(Outcome.NOTHING);
    }

    @Test
    void aLateRunStillGivesAFullWeekAfterTheOnlyWarning() {
        var person = unused("missed@example.test", AGE.plusDays(100));
        assertThat(handle(person, now)).isEqualTo(Outcome.WARNED);
        assertThat(handle(person, now.plus(Duration.ofDays(6)))).isEqualTo(Outcome.NOTHING);
        assertThat(handle(person, now.plus(Duration.ofDays(7)))).isEqualTo(Outcome.REMOVED);
    }

    @Test
    void anOwnerOfATeamOthersUseIsKeptAndItsAdminsAreTold() {
        var owner = unused("absent-owner@example.test", AGE.plusDays(10));
        var admin = fixtures.user("team-admin@example.test", "Admin");
        var team = fixtures.team("Still used", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, admin, TeamRole.ADMIN, Instant.now());
        warnedLongAgo(owner);

        assertThat(handle(owner, now)).isEqualTo(Outcome.KEPT);

        assertThat(reload(owner).getDeletedAt()).isNull();
        assertThat(mail.last().to()).isEqualTo("team-admin@example.test");
        assertThat(mail.last().subject()).isEqualTo("Właściciel zespołu Still used od dawna nie korzysta z HourForge");
        assertThat(inactive.due(now)).doesNotContain(owner.getId());
    }

    @Test
    void teamsTheRemovedPersonOwnedAloneGoWithTheAccount() {
        var owner = unused("solo-owner@example.test", AGE.plusDays(10));
        var team = fixtures.team("Only me", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        warnedLongAgo(owner);

        assertThat(handle(owner, now)).isEqualTo(Outcome.REMOVED);
        assertThat(jdbc.queryForObject("select count(*) from teams where id = ?", Integer.class, team.getId())).isZero();
    }

    @Test
    void operatorsAndRecentlyActivePeopleAreLeftAlone() {
        var operator = unused("operator@example.test", AGE.plusDays(10));
        warnedLongAgo(operator);
        var active = unused("active@example.test", Duration.ofDays(10));

        assertThat(handle(operator, now)).isEqualTo(Outcome.NOTHING);
        assertThat(inactive.due(now)).doesNotContain(active.getId());
        assertThat(handle(active, now)).isEqualTo(Outcome.NOTHING);
        assertThat(mail.sent()).isEmpty();
    }

    private User unused(String email, Duration idleFor) {
        var user = fixtures.user(email, email.substring(0, email.indexOf('@')));
        jdbc.update("update users set last_active_at = ? where id = ?", Timestamp.from(now.minus(idleFor)), user.getId());
        entityManager.clear();
        return user;
    }

    private void warnedLongAgo(User user) {
        jdbc.update("update users set inactivity_warned_days = 7, inactivity_warned_at = ? where id = ?",
                Timestamp.from(now.minus(Duration.ofDays(20))), user.getId());
        entityManager.clear();
    }

    private Outcome handle(User user, Instant at) {
        var outcome = inactive.handle(user.getId(), at);
        entityManager.flush();
        entityManager.clear();
        return outcome;
    }

    private User reload(User user) {
        return users.findById(user.getId()).orElseThrow();
    }
}
