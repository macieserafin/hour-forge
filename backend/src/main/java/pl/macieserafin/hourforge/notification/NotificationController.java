package pl.macieserafin.hourforge.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    /** Named in the List-Unsubscribe header of optional e-mails; mail providers post to it without a session or CSRF token. */
    public static final String ONE_CLICK_PATH = "/api/notifications/unsubscribe/one-click";

    private static final java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{43}");

    public record UnsubscribeRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token, @NotNull NotificationKind kind) {
    }

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    /** Public on purpose: the link in an e-mail must work without signing in. */
    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        notifications.unsubscribe(request.token(), request.kind());
        return ResponseEntity.noContent().build();
    }

    /**
     * One-click unsubscribe (RFC 8058): the mail provider posts {@code List-Unsubscribe=One-Click} to the address from the
     * header. The random token is the only permission, as in the link of the footer; it switches off one kind of mail.
     */
    @PostMapping("/unsubscribe/one-click")
    public ResponseEntity<Void> oneClick(@RequestParam String token, @RequestParam String kind) {
        if (!TOKEN.matcher(token).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        NotificationKind parsed;
        try {
            parsed = NotificationKind.valueOf(kind);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        notifications.unsubscribe(token, parsed);
        return ResponseEntity.noContent().build();
    }
}
