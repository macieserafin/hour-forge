package pl.macieserafin.hourforge.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.mail.MailContent;
import pl.macieserafin.hourforge.mail.Mailer;
import pl.macieserafin.hourforge.security.RateLimitProperties;
import pl.macieserafin.hourforge.security.RateLimiter;
import pl.macieserafin.hourforge.security.TooManyRequestsException;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Clock;
import java.util.UUID;

@Service
public class EmailVerificationService {
    private final UserRepository users;
    private final EmailTokenService tokens;
    private final Mailer mailer;
    private final RateLimiter limiter;
    private final RateLimitProperties limits;
    private final Clock clock;

    public EmailVerificationService(UserRepository users, EmailTokenService tokens, Mailer mailer,
                                    RateLimiter limiter,
                                    RateLimitProperties limits, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.mailer = mailer;
        this.limiter = limiter;
        this.limits = limits;
        this.clock = clock;
    }

    /** Sends a confirmation link to a new, still unconfirmed account. */
    @Transactional
    public void sendConfirmation(User user) {
        if (user.isEmailVerified()) return;
        var token = tokens.issue(user, EmailTokenPurpose.VERIFY_EMAIL, null);
        mailer.send(user.getEmail(), MailContent.builder("Potwierdź adres e-mail w HourForge")
                .title("Potwierdź adres e-mail")
                .preheader("Jeden klik i możesz zacząć mierzyć czas. Link jest ważny przez 24 godziny.")
                .greeting(user.getDisplayName())
                .paragraph("Dziękujemy za założenie konta w HourForge. Zostało jeszcze potwierdzenie adresu e-mail.")
                .action("Potwierdź swój adres e-mail, otwierając link (ważny 24 godziny):", "Potwierdź adres e-mail",
                        mailer.url("/verify-email/" + token))
                .note("Jeśli to nie Ty zakładałeś konto, zignoruj tę wiadomość.")
                .build());
    }

    @Transactional
    public void confirm(String rawToken) {
        var token = tokens.redeem(rawToken, EmailTokenPurpose.VERIFY_EMAIL);
        var user = token.getUser();
        if (!user.isEmailVerified()) user.setEmailVerifiedAt(clock.instant());
    }

    /**
     * Public resend: always looks the same to the caller, whether or not the address belongs to an account,
     * so it cannot be used to discover accounts. Only the per-address limit is silent; the per-IP limit answers 429.
     */
    @Transactional
    public void resendByEmail(String email, String ip) {
        limiter.tryAcquire("email-verification-ip:" + ip, limits.emailVerificationIp())
                .ifPresent(retryAfter -> { throw new TooManyRequestsException(retryAfter); });
        if (email == null || email.isBlank()) return;
        users.findByEmailIgnoreCase(email.strip()).filter(user -> !user.isEmailVerified()).ifPresent(user -> {
            if (limiter.tryAcquire("email-verification:" + user.getId(), limits.emailVerification()).isEmpty()) {
                sendConfirmation(user);
            }
        });
    }

}
