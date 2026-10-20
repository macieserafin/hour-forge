package pl.macieserafin.hourforge.jobs.integration;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.macieserafin.hourforge.jobs.LongTimerReminderService;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;

class LongTimerReminderIT extends ApiIntegrationTestSupport {
    @Autowired LongTimerReminderService reminders;
    @Autowired TimeEntryRepository entries;
    @Autowired EntityManager entityManager;

    private final Instant now = Instant.parse("2026-10-21T20:00:00Z");
    private Team team;
    private Project project;

    @BeforeEach
    void setup() {
        team = fixtures.team("Studio", "Europe/Warsaw");
        project = fixtures.project(team, "Website", null);
    }

    private TimeEntry timer(String email, long hoursAgo, long pausedHours) {
        User user = fixtures.user(email, "Worker " + email);
        fixtures.member(team, user, TeamRole.MEMBER, now.minus(30, ChronoUnit.DAYS));
        var entry = new TimeEntry(user, team, project, EntryType.TIMER, LocalDate.of(2026, 10, 21),
                now.minus(hoursAgo, ChronoUnit.HOURS), null, null, "Secret description of the work");
        entry.setPausedDurationMicros(pausedHours * 3_600_000_000L);
        return entries.saveAndFlush(entry);
    }

    @Test
    void remindsOncePerTimerAfterTenHoursOfActualWork() {
        var entry = timer("long@example.com", 11, 0);
        assertThat(reminders.remindDue(now)).isEqualTo(1);
        assertThat(mail.sent()).hasSize(1);
        var message = mail.last();
        assertThat(message.to()).isEqualTo("long@example.com");
        assertThat(message.subject()).contains("11 godz.");
        assertThat(message.body()).contains("Website").contains("Studio").contains("/time?team=" + team.getId())
                .doesNotContain("Secret description");
        entityManager.flush();
        entityManager.clear();
        assertThat(entries.findById(entry.getId()).orElseThrow().getLongTimerRemindedAt()).isEqualTo(now);

        assertThat(reminders.remindDue(now.plus(1, ChronoUnit.HOURS))).isZero();
        assertThat(mail.sent()).hasSize(1);
    }

    @Test
    void doesNotCountPausesNorRemindShortTimers() {
        timer("short@example.com", 9, 0);
        timer("paused-earlier@example.com", 12, 3);
        assertThat(reminders.remindDue(now)).isZero();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void leavesPausedAndFinishedTimersAlone() {
        var paused = timer("paused@example.com", 14, 0);
        paused.setPausedAt(now.minus(1, ChronoUnit.HOURS));
        var finished = timer("finished@example.com", 14, 0);
        finished.setEndedAt(now.minus(1, ChronoUnit.HOURS));
        finished.setDurationSeconds(13 * 3600);
        entries.flush();
        assertThat(reminders.remindDue(now)).isZero();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void aTimerThatReachesTheLimitLaterIsRemindedThen() {
        timer("later@example.com", 9, 0);
        assertThat(reminders.remindDue(now)).isZero();
        assertThat(reminders.remindDue(now.plus(90, ChronoUnit.MINUTES))).isEqualTo(1);
    }
}
