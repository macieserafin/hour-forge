package pl.macieserafin.hourforge.jobs;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.macieserafin.hourforge.mail.MailContent;
import pl.macieserafin.hourforge.mail.MailContent.Detail;
import pl.macieserafin.hourforge.mail.Mailer;
import pl.macieserafin.hourforge.preferences.UserPreferences;
import pl.macieserafin.hourforge.preferences.UserPreferencesRepository;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;

/** Tells people, once per timer, that a timer has been running for a long time of actual work (pauses do not count). */
@Service
public class LongTimerReminderService {
    private static final Logger log = LoggerFactory.getLogger(LongTimerReminderService.class);
    private static final Duration MIN_THRESHOLD = Duration.ofHours(4);
    private static final DateTimeFormatter STARTED = DateTimeFormatter.ofPattern("d.MM.yyyy 'o' HH:mm");

    private final JobsProperties properties;
    private final TimeEntryRepository entries;
    private final Mailer mailer;
    private final UserPreferencesRepository preferences;
    private final pl.macieserafin.hourforge.notification.NotificationService notifications;
    private final TransactionTemplate transaction;

    public LongTimerReminderService(JobsProperties properties, TimeEntryRepository entries, Mailer mailer,
                                    UserPreferencesRepository preferences,
                                    pl.macieserafin.hourforge.notification.NotificationService notifications,
                                    PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.notifications = notifications;
        this.properties = properties;
        this.entries = entries;
        this.mailer = mailer;
        this.preferences = preferences;
    }

    /**
     * Sends the reminders that are due at {@code now} and returns how many. A paused timer is left alone. Every timer has
     * its own transaction: it is marked and the mail leaves together at its commit, and a timer that fails is skipped
     * without repeating or holding back the others.
     */
    public int remindDue(Instant now) {
        // The earliest anybody can be reminded is the smallest allowed threshold; each person's own limit is checked below.
        var candidates = transaction.execute(status -> entries.findDueForLongTimerReminder(now.minus(MIN_THRESHOLD))
                .stream().map(TimeEntry::getId).toList());
        int sent = 0;
        for (var entryId : candidates) {
            try {
                if (Boolean.TRUE.equals(transaction.execute(status -> remindIfDue(entryId, now)))) sent++;
            } catch (RuntimeException exception) {
                log.error("Long timer reminder failed for entryId={}", entryId, exception);
            }
        }
        return sent;
    }

    private boolean remindIfDue(java.util.UUID entryId, Instant now) {
        var entry = entries.findById(entryId).orElse(null);
        if (entry == null || entry.getEndedAt() != null || entry.getPausedAt() != null || entry.getLongTimerRemindedAt() != null) return false;
        var own = preferences.findById(entry.getUser().getId()).orElse(null);
        // A person who turned the e-mail off is not marked as reminded, so turning it on again later is respected.
        if (own != null && !own.isLongTimerEmail()) return false;
        var limit = own == null ? properties.longTimerAfter() : Duration.ofHours(own.getLongTimerHours());
        if (netWork(entry, now).compareTo(limit) < 0) return false;
        entry.setLongTimerRemindedAt(now.truncatedTo(ChronoUnit.MICROS));
        entries.saveAndFlush(entry);
        send(entry, netWork(entry, now));
        return true;
    }

    private static Duration netWork(TimeEntry entry, Instant now) {
        return Duration.between(entry.getStartedAt(), now).minus(entry.getPausedDurationMicros(), ChronoUnit.MICROS);
    }

    private void send(TimeEntry entry, Duration worked) {
        var user = entry.getUser();
        var zone = ZoneId.of(entry.getTeam().getTimezone());
        long hours = worked.toHours();
        var team = entry.getTeam();
        var kind = pl.macieserafin.hourforge.notification.NotificationKind.LONG_TIMER;
        mailer.notify(user.getEmail(), MailContent.builder("Twój timer w HourForge działa od ponad " + hours + " godz.")
                .title("Timer działa od ponad " + hours + " godz.")
                .preheader("Jeśli o nim zapomniałeś, zatrzymaj go i popraw czas wpisu.")
                .greeting(user.getDisplayName())
                .paragraph("Twój timer działa już " + hours + " godz. czystej pracy:")
                .details(List.of(new Detail("Projekt", entry.getProject().getName()), new Detail("Zespół", team.getName()),
                        new Detail("Start", entry.getStartedAt().atZone(zone).format(STARTED))))
                .action("Jeśli o nim zapomniałeś, zatrzymaj go i popraw czas wpisu:", "Otwórz timer", mailer.url("/time?team=" + team.getId()))
                .note("To jedyne przypomnienie o tym timerze. Jeśli pracujesz dalej, nic nie musisz robić.")
                .build(), notifications.unsubscribeLinks(user, kind));
    }
}
