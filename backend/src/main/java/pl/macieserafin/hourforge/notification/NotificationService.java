package pl.macieserafin.hourforge.notification;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.preferences.UserPreferences;
import pl.macieserafin.hourforge.preferences.UserPreferencesRepository;
import pl.macieserafin.hourforge.user.User;

/** Decides whether an optional e-mail is wanted and gives every one of them a way to stop. */
@Service
public class NotificationService {
    private final UserPreferencesRepository preferences;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public NotificationService(UserPreferencesRepository preferences, @Value("${app.frontend-url}") String frontendUrl) {
        this.preferences = preferences;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    /** Nobody has to opt in: a person without stored settings gets every kind. */
    @Transactional(readOnly = true)
    public boolean allows(UUID userId, NotificationKind kind) {
        var stored = preferences.findById(userId).orElse(null);
        if (stored == null) return true;
        return switch (kind) {
            case TEAM_EVENTS -> stored.isTeamEventEmails();
            case WEEKLY_REPORT -> stored.isWeeklyReportEmail();
            case LONG_TIMER -> stored.isLongTimerEmail();
        };
    }

    /** The closing lines of an optional e-mail: the link that switches this kind off and where all of them are set. */
    @Transactional
    public String footer(User person, NotificationKind kind) {
        var stored = preferences.findById(person.getId()).orElseGet(() -> new UserPreferences(person.getId()));
        if (stored.getUnsubscribeToken() == null) {
            var bytes = new byte[32];
            random.nextBytes(bytes);
            stored.setUnsubscribeToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            preferences.saveAndFlush(stored);
        }
        return "\n--\nNie chcesz takich wiadomości? Wypisz się jednym kliknięciem:\n" + frontendUrl + "/unsubscribe/" + stored.getUnsubscribeToken()
                + "?kind=" + kind.name() + "\nWszystkie powiadomienia ustawisz w koncie: " + frontendUrl + "/account\n";
    }

    /** Switches one kind off for the person the link was made for. An unknown link says nothing about who has one. */
    @Transactional
    public void unsubscribe(String token, NotificationKind kind) {
        var stored = preferences.findByUnsubscribeToken(token).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        switch (kind) {
            case TEAM_EVENTS -> stored.setTeamEventEmails(false);
            case WEEKLY_REPORT -> stored.setWeeklyReportEmail(false);
            case LONG_TIMER -> stored.setLongTimerEmail(false);
        }
        preferences.saveAndFlush(stored);
    }
}
