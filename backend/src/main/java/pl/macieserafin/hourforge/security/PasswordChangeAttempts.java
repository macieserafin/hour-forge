package pl.macieserafin.hourforge.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Throttles wrong "current password" answers, so a hijacked session cannot be used to guess the password. */
@Component
public class PasswordChangeAttempts {
    private static final Logger log = LoggerFactory.getLogger(PasswordChangeAttempts.class);

    private final RateLimiter limiter;
    private final RateLimitProperties limits;

    public PasswordChangeAttempts(RateLimiter limiter, RateLimitProperties limits) {
        this.limiter = limiter;
        this.limits = limits;
    }

    public void requireNotBlocked(UUID userId) {
        limiter.blocked(key(userId)).ifPresent(retryAfter -> { throw new TooManyRequestsException(retryAfter); });
    }

    public void recordFailure(UUID userId) {
        log.info("Wrong current password on password change: userId={}", userId);
        if (limiter.recordFailure(key(userId), limits.passwordChange())) {
            log.warn("Password change temporarily blocked after repeated failures: userId={}", userId);
        }
    }

    public void reset(UUID userId) {
        limiter.reset(key(userId));
    }

    private static String key(UUID userId) { return "password-change:" + userId; }
}
