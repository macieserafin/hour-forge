package pl.macieserafin.hourforge.jobs;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The recurring jobs. One running instance is assumed (several would need a shared lock, see roadmap B8). A failure is
 * logged and the next run tries again; the jobs are safe to repeat.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class ScheduledJobs {
    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final RetentionService retention;
    private final LongTimerReminderService reminders;
    private final WeeklyReportService weeklyReports;
    private final InactiveAccountService inactiveAccounts;
    private final Clock clock;

    public ScheduledJobs(RetentionService retention, LongTimerReminderService reminders, WeeklyReportService weeklyReports,
                         InactiveAccountService inactiveAccounts, Clock clock) {
        this.inactiveAccounts = inactiveAccounts;
        this.weeklyReports = weeklyReports;
        this.retention = retention;
        this.reminders = reminders;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    public void purge() {
        try {
            var purged = retention.purge(clock.instant());
            log.info("Retention job removed {} accounts, {} tokens, {} OAuth states, {} invitations, {} sign-ins",
                    purged.accounts(), purged.tokens(), purged.oauthStates(), purged.invitations(), purged.logins());
        } catch (RuntimeException exception) {
            log.error("Retention job failed", exception);
        }
    }

    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT15M")
    public void sendWeeklyReports() {
        try {
            int sent = weeklyReports.sendDue(clock.instant());
            if (sent > 0) log.info("Sent weekly reports for {} teams", sent);
        } catch (RuntimeException exception) {
            log.error("Weekly report job failed", exception);
        }
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT15M")
    public void remindAboutLongTimers() {
        try {
            int sent = reminders.remindDue(clock.instant());
            if (sent > 0) log.info("Sent {} long timer reminders", sent);
        } catch (RuntimeException exception) {
            log.error("Long timer reminder job failed", exception);
        }
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
    public void handleInactiveAccounts() {
        var now = clock.instant();
        int removed = 0;
        for (var userId : inactiveAccounts.due(now)) {
            // One account at a time: a problem with one (e.g. a team changing meanwhile) does not stop the others.
            try {
                if (inactiveAccounts.handle(userId, now) == InactiveAccountService.Outcome.REMOVED) removed++;
            } catch (RuntimeException exception) {
                log.error("Inactive account job failed for userId={}", userId, exception);
            }
        }
        if (removed > 0) log.info("Removed {} inactive accounts", removed);
    }
}
