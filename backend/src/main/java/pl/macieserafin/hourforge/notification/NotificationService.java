package pl.macieserafin.hourforge.notification;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.mail.Mailer;
import pl.macieserafin.hourforge.preferences.UserPreferences;
import pl.macieserafin.hourforge.preferences.UserPreferencesRepository;
import pl.macieserafin.hourforge.security.Tokens;
import pl.macieserafin.hourforge.user.User;

/** Decides whether an optional e-mail is wanted and gives every one of them a way to stop. */
@Service
public class NotificationService {
    private final UserPreferencesRepository preferences;
    private final String frontendUrl;

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

    /** The addresses an optional e-mail offers: the page that switches this kind off, the one-click address and the settings. */
    @Transactional
    public Mailer.Unsubscribe unsubscribeLinks(User person, NotificationKind kind) {
        var stored = preferences.findById(person.getId()).orElseGet(() -> new UserPreferences(person.getId()));
        if (stored.getUnsubscribeToken() == null) {
            stored.setUnsubscribeToken(Tokens.random());
            preferences.saveAndFlush(stored);
        }
        var query = "token=" + stored.getUnsubscribeToken() + "&kind=" + kind.name();
        return new Mailer.Unsubscribe(frontendUrl + "/unsubscribe/" + stored.getUnsubscribeToken() + "?kind=" + kind.name(),
                frontendUrl + NotificationController.ONE_CLICK_PATH + "?" + query, frontendUrl + "/account#notifications");
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
