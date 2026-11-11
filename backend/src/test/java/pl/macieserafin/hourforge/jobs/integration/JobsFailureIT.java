package pl.macieserafin.hourforge.jobs.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.macieserafin.hourforge.jobs.LongTimerReminderService;
import pl.macieserafin.hourforge.jobs.WeeklyReportService;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real commits on PostgreSQL: when the commit of one team or timer is rejected, the others are marked and mailed exactly
 * once, the rejected one is neither marked nor mailed, and a later run does not repeat anything.
 */
@TestPropertySource(properties = "test.mail.after-commit=true")
class JobsFailureIT extends IntegrationTestSupport {
    private static final Instant MONDAY_MORNING = Instant.parse("2026-10-19T06:30:00Z");
    private static final Instant NOW = Instant.parse("2026-10-21T20:00:00Z");

    @Autowired WeeklyReportService reports;
    @Autowired LongTimerReminderService reminders;
    @Autowired TeamRepository teams;
    @Autowired TimeEntryRepository entries;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> teamIds = new ArrayList<>();

    @AfterEach
    void removeOwnedFixtures() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("drop trigger if exists reject_commit on teams");
            jdbc.execute("drop trigger if exists reject_commit on time_entries");
            jdbc.execute("drop function if exists reject_commit()");
            for (UUID id : teamIds) {
                jdbc.update("delete from time_entries where team_id = ?", id);
                jdbc.update("delete from projects where team_id = ?", id);
                jdbc.update("delete from team_members where team_id = ?", id);
                jdbc.update("delete from teams where id = ?", id);
            }
            for (UUID id : userIds) {
                jdbc.update("delete from user_preferences where user_id = ?", id);
                jdbc.update("delete from users where id = ?", id);
            }
        });
    }

    @Test
    void aTeamWhoseCommitFailsDoesNotRepeatOrBlockTheReportsOfOtherTeams() {
        var healthy = weeklyTeam();
        var broken = weeklyTeam();
        rejectCommitOf("teams", broken.getId());

        assertThat(reports.sendDue(MONDAY_MORNING)).isEqualTo(1);
        assertThat(reports.sendDue(MONDAY_MORNING.plus(15, ChronoUnit.MINUTES))).isZero();

        assertThat(mail.sent()).extracting(m -> m.to()).containsExactly(ownerEmail(healthy));
        assertThat(sentFor(healthy)).isNotNull();
        assertThat(sentFor(broken)).isNull();
    }

    @Test
    void aTimerWhoseCommitFailsDoesNotRepeatOrBlockTheRemindersOfOthers() {
        var team = fixtureTeam();
        var project = fixtures.project(team, "Website", null);
        var healthy = timer(team, project, "healthy-timer");
        var broken = timer(team, project, "broken-timer");
        rejectCommitOf("time_entries", broken.getId());

        assertThat(reminders.remindDue(NOW)).isEqualTo(1);
        assertThat(reminders.remindDue(NOW.plus(15, ChronoUnit.MINUTES))).isZero();

        assertThat(mail.sent()).extracting(m -> m.to()).containsExactly(healthy.getUser().getEmail());
        assertThat(entries.findById(healthy.getId()).orElseThrow().getLongTimerRemindedAt()).isNotNull();
        assertThat(entries.findById(broken.getId()).orElseThrow().getLongTimerRemindedAt()).isNull();
    }

    private Team weeklyTeam() {
        var team = fixtureTeam();
        var owner = account();
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        team.setWeeklyReportEnabled(true);
        return teams.saveAndFlush(team);
    }

    private Team fixtureTeam() {
        var team = fixtures.team("Failure " + UUID.randomUUID(), "Europe/Warsaw");
        teamIds.add(team.getId());
        return team;
    }

    private TimeEntry timer(Team team, Project project, String name) {
        User user = account();
        fixtures.member(team, user, TeamRole.MEMBER, NOW.minus(30, ChronoUnit.DAYS));
        return entries.saveAndFlush(new TimeEntry(user, team, project, EntryType.TIMER, LocalDate.of(2026, 10, 21),
                NOW.minus(11, ChronoUnit.HOURS), null, null, name));
    }

    private User account() {
        var user = fixtures.user(UUID.randomUUID() + "@jobs-failure.local", "Jobs Test");
        userIds.add(user.getId());
        return user;
    }

    private String ownerEmail(Team team) {
        return jdbc.queryForObject("select u.email from team_members m join users u on u.id = m.user_id where m.team_id = ?", String.class, team.getId());
    }

    private java.sql.Date sentFor(Team team) {
        return jdbc.queryForObject("select weekly_report_sent_for from teams where id = ?", java.sql.Date.class, team.getId());
    }

    /** A deferred constraint trigger turns the commit of the one row into a failure, as a lost connection or a full disk would. */
    private void rejectCommitOf(String table, UUID id) {
        jdbc.execute("create function reject_commit() returns trigger language plpgsql as $$ begin "
                + "if new.id::text = tg_argv[0] then raise exception 'commit rejected'; end if; return null; end $$");
        jdbc.execute("create constraint trigger reject_commit after update on " + table
                + " initially deferred for each row execute function reject_commit('" + id + "')");
    }
}
