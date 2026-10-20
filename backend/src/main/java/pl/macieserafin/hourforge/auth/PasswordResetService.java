package pl.macieserafin.hourforge.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.security.RateLimitProperties;
import pl.macieserafin.hourforge.security.RateLimiter;
import pl.macieserafin.hourforge.security.TooManyRequestsException;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Clock;

@Service
public class PasswordResetService {
    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final EmailTokenService tokens;
    private final AccountMailService mail;
    private final PasswordEncoder encoder;
    private final RateLimiter limiter;
    private final RateLimitProperties limits;
    private final Clock clock;

    public PasswordResetService(UserRepository users, UserCredentialRepository credentials, EmailTokenService tokens,
                                AccountMailService mail, PasswordEncoder encoder, RateLimiter limiter,
                                RateLimitProperties limits, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.tokens = tokens;
        this.mail = mail;
        this.encoder = encoder;
        this.limiter = limiter;
        this.limits = limits;
        this.clock = clock;
    }

    /**
     * Looks the same for every address, so it cannot be used to discover accounts. Only the per-IP limit answers 429;
     * the per-account limit silently stops sending.
     */
    @Transactional
    public void request(String email, String ip) {
        limiter.tryAcquire("password-reset-ip:" + ip, limits.emailVerificationIp())
                .ifPresent(retryAfter -> { throw new TooManyRequestsException(retryAfter); });
        if (email == null || email.isBlank()) return;
        users.findByEmailIgnoreCase(email.strip()).ifPresent(user -> {
            if (limiter.tryAcquire("password-reset:" + user.getId(), limits.emailVerification()).isEmpty()) {
                mail.sendPasswordReset(user);
            }
        });
    }

    /** Sets a new password and ends every session. The link is only used up when the new password is acceptable. */
    @Transactional
    public void confirm(String rawToken, String newPassword) {
        PasswordPolicy.require(newPassword);
        var token = tokens.redeem(rawToken, EmailTokenPurpose.RESET_PASSWORD);
        var user = token.getUser();
        var credential = credentials.findLockedByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "The link is invalid or has expired"));
        credential.setPasswordHash(encoder.encode(newPassword));
        credential.invalidateSessions();
        // Opening the link proves control of the mailbox.
        if (!user.isEmailVerified()) user.setEmailVerifiedAt(clock.instant());
        credentials.flush();
    }
}
