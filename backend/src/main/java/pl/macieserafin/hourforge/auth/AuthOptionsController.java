package pl.macieserafin.hourforge.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.mail.EmailSender;
import pl.macieserafin.hourforge.mail.EmailVerificationProperties;

/** Public switches the sign-in and registration screens need to show the right options. */
@RestController
public class AuthOptionsController {
    private final EmailSender sender;
    private final EmailVerificationProperties verification;

    public AuthOptionsController(EmailSender sender, EmailVerificationProperties verification) {
        this.sender = sender;
        this.verification = verification;
    }

    @GetMapping("/api/auth/options")
    public Options options() {
        return new Options(sender.enabled(), verification.required());
    }

    public record Options(boolean emailEnabled, boolean emailVerificationRequired) {
    }
}
