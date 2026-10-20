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
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;

/** Tells people, once per timer, that a timer has been running for a long time of actual work (pauses do not count). */
@Service
public class LongTimerReminderService {
    private static final DateTimeFormatter STARTED = DateTimeFormatter.ofPattern("d.MM.yyyy 'o' HH:mm");

    private final JobsProperties properties;
    private final TimeEntryRepository entries;
    private final EmailSender sender;
    private final String frontendUrl;

    public LongTimerReminderService(JobsProperties properties, TimeEntryRepository entries, EmailSender sender,
                                    @Value("${app.frontend-url}") String frontendUrl) {
        this.properties = properties;
        this.entries = entries;
        this.sender = sender;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    /** Sends the reminders that are due at {@code now} and returns how many. A paused timer is left alone. */
    @Transactional
    public int remindDue(Instant now) {
        List<TimeEntry> candidates = entries.findDueForLongTimerReminder(now.minus(properties.longTimerAfter()));
        int sent = 0;
        for (var entry : candidates) {
            if (netWork(entry, now).compareTo(properties.longTimerAfter()) < 0) continue;
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
                        + "\n\nTo jedyne przypomnienie o tym timerze. Jeśli pracujesz dalej, nic nie musisz robić.\n");
    }
}
