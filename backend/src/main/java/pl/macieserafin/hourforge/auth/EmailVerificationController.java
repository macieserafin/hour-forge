package pl.macieserafin.hourforge.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/auth/email-verification")
public class EmailVerificationController {
    private final EmailVerificationService verification;

    public EmailVerificationController(EmailVerificationService verification) {
        this.verification = verification;
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@RequestBody TokenRequest request) {
        verification.confirm(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend")
    public ResponseEntity<Void> resend(@RequestBody ResendRequest request, HttpServletRequest http) {
        verification.resendByEmail(request.email(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    public record TokenRequest(@NotBlank @Size(max = 128) String token) {
        @Override
        public String toString() {
            return "TokenRequest[redacted]";
        }
    }

    public record ResendRequest(@Size(max = 255) String email) {
    }
}
