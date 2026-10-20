package pl.macieserafin.hourforge.account;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.auth.AccountMailService;
import pl.macieserafin.hourforge.auth.EmailTokenPurpose;
import pl.macieserafin.hourforge.auth.EmailTokenService;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.security.PasswordChangeAttempts;
import pl.macieserafin.hourforge.security.RateLimitProperties;
import pl.macieserafin.hourforge.security.RateLimiter;
import pl.macieserafin.hourforge.security.TooManyRequestsException;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Clock;
import java.util.UUID;

@Service
public class EmailChangeService {
    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final EmailTokenService tokens;
    private final AccountMailService mail;
    private final PasswordEncoder encoder;
    private final PasswordChangeAttempts attempts;
    private final RateLimiter limiter;
    private final RateLimitProperties limits;
    private final Clock clock;

    public EmailChangeService(UserRepository users, UserCredentialRepository credentials, EmailTokenService tokens,
                              AccountMailService mail, PasswordEncoder encoder, PasswordChangeAttempts attempts,
                              RateLimiter limiter, RateLimitProperties limits, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.tokens = tokens;
        this.mail = mail;
        this.encoder = encoder;
        this.attempts = attempts;
        this.limiter = limiter;
        this.limits = limits;
        this.clock = clock;
    }

    /**
     * Sends a link to the new address. The answer is the same whether or not another account already uses that
     * address, so the form cannot be used to discover accounts.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void request(UUID userId, EmailChangeRequest request) {
        attempts.requireNotBlocked(userId);
        var password = request.currentPassword();
        if (password == null || password.isEmpty() || password.length() > 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required");
        }
        var credential = credentials.findLockedByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!encoder.matches(password, credential.getPasswordHash())) {
            attempts.recordFailure(userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        var user = credential.getUser();
        if (user.getEmail().equalsIgnoreCase(request.newEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The new address is the same as the current one");
        }
        limiter.tryAcquire("email-change:" + userId, limits.emailVerification())
                .ifPresent(retryAfter -> { throw new TooManyRequestsException(retryAfter); });
        attempts.reset(userId);
        if (users.findByEmailIgnoreCase(request.newEmail()).isEmpty()) {
            mail.sendEmailChange(user, request.newEmail());
        }
    }

    /** Switches the sign-in address and ends every session, because the old address no longer identifies the account. */
    @Transactional
    public void confirm(String rawToken) {
        var token = tokens.redeem(rawToken, EmailTokenPurpose.CHANGE_EMAIL);
        var user = token.getUser();
        var newEmail = token.getNewEmail();
        if (users.findByEmailIgnoreCase(newEmail).filter(other -> !other.getId().equals(user.getId())).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The address is already used by another account");
        }
        var oldEmail = user.getEmail();
        user.setEmail(newEmail);
        user.setEmailVerifiedAt(clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The address is already used by another account");
        }
        credentials.findLockedByUserId(user.getId()).ifPresent(credential -> credential.invalidateSessions());
        credentials.flush();
        mail.sendEmailChanged(oldEmail, newEmail);
    }
}
