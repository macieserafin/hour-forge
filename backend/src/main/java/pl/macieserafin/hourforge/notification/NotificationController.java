package pl.macieserafin.hourforge.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
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
}
