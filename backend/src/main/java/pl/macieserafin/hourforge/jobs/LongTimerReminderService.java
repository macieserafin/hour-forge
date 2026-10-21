package pl.macieserafin.hourforge.jobs;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.mail.EmailSender;
import pl.macieserafin.hourforge.preferences.UserPreferences;
import pl.macieserafin.hourforge.preferences.UserPreferencesRepository;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;

/** Tells people, once per timer, that a timer has been running for a long time of actual work (pauses do not count). */
@Service
public class LongTimerReminderService {
    private static final Duration MIN_THRESHOLD = Duration.ofHours(4);
    private static final DateTimeFormatter STARTED = DateTimeFormatter.ofPattern("d.MM.yyyy 'o' HH:mm");

    private final JobsProperties properties;
    private final TimeEntryRepository entries;
    private final EmailSender sender;
    private final UserPreferencesRepository preferences;
    private final String frontendUrl;
    private final pl.macieserafin.hourforge.notification.NotificationService notifications;

    public LongTimerReminderService(JobsProperties properties, TimeEntryRepository entries, EmailSender sender,
                                    UserPreferencesRepository preferences,
                                    @Value("${app.frontend-url}") String frontendUrl,
                                    pl.macieserafin.hourforge.notification.NotificationService notifications) {
        this.notifications = notifications;
        this.properties = properties;
        this.entries = entries;
        this.sender = sender;
        this.preferences = preferences;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    /** Sends the reminders that are due at {@code now} and returns how many. A paused timer is left alone. */
    @Transactional
    public int remindDue(Instant now) {
        // The earliest anybody can be reminded is the smallest allowed threshold; each person's own limit is checked below.
        List<TimeEntry> candidates = entries.findDueForLongTimerReminder(now.minus(MIN_THRESHOLD));
        int sent = 0;
        for (var entry : candidates) {
            var own = preferences.findById(entry.getUser().getId()).orElse(null);
            // A person who turned the e-mail off is not marked as reminded, so turning it on again later is respected.
            if (own != null && !own.isLongTimerEmail()) continue;
            var limit = own == null ? properties.longTimerAfter() : Duration.ofHours(own.getLongTimerHours());
            if (netWork(entry, now).compareTo(limit) < 0) continue;
            entry.setLongTimerRemindedAt(now.truncatedTo(ChronoUnit.MICROS));
            send(entry, netWork(entry, now));
            sent++;
        }
        return sent;
    }

    private static Duration netWork(TimeEntry entry, Instant now) {
        return Duration.between(entry.getStartedAt(), now).minus(entry.getPausedDurationMicros(), ChronoUnit.MICROS);
    }

    private void send(TimeEntry entry, Duration worked) {
        var user = entry.getUser();
        var zone = ZoneId.of(entry.getTeam().getTimezone());
        long hours = worked.toHours();
        sender.send(user.getEmail(), "Twój timer w HourForge działa od ponad " + hours + " godz.",
                "Cześć " + user.getDisplayName() + ",\n\nTwój timer w projekcie " + entry.getProject().getName() + " (zespół "
                        + entry.getTeam().getName() + ") został uruchomiony " + entry.getStartedAt().atZone(zone).format(STARTED)
                        + " i działa już " + hours + " godz. czystej pracy. Jeśli o nim zapomniałeś, zatrzymaj go i popraw czas wpisu:\n"
                        + frontendUrl + "/time?team=" + entry.getTeam().getId()
                        + "\n\nTo jedyne przypomnienie o tym timerze. Jeśli pracujesz dalej, nic nie musisz robić.\n"
                        + notifications.footer(user, pl.macieserafin.hourforge.notification.NotificationKind.LONG_TIMER));
    }
}
