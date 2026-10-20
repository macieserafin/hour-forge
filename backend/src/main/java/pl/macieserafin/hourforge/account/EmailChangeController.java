package pl.macieserafin.hourforge.account;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

@RestController
@RequestMapping("/api")
public class EmailChangeController {
    private final EmailChangeService change;

    public EmailChangeController(EmailChangeService change) {
        this.change = change;
    }

    @PostMapping("/account/email")
    public ResponseEntity<Void> request(@AuthenticationPrincipal AppUserPrincipal principal,
                                        @Valid @RequestBody EmailChangeRequest request) {
        change.request(principal.getUserId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/email-change/confirm")
    public ResponseEntity<Void> confirm(@RequestBody TokenRequest request) {
        change.confirm(request.token());
        return ResponseEntity.noContent().build();
    }

    public record TokenRequest(String token) {
        @Override
        public String toString() {
            return "TokenRequest[redacted]";
        }
    }
}
