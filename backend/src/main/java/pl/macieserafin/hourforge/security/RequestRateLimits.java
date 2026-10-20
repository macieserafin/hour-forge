package pl.macieserafin.hourforge.security;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Limits how often accounts and resources can be created; exceeding a limit results in HTTP 429. */
@Component
public class RequestRateLimits {
    private final RateLimiter limiter;
    private final RateLimitProperties limits;

    public RequestRateLimits(RateLimiter limiter, RateLimitProperties limits) {
        this.limiter = limiter;
        this.limits = limits;
    }

    public void registration(String ip) { acquire("registration:" + ip, limits.registration()); }

    public void teamCreation(UUID userId) { acquire("team-creation:" + userId, limits.teamCreation()); }

    public void invitationCreation(UUID userId) { acquire("invitation-creation:" + userId, limits.invitationCreation()); }

    public void projectCreation(UUID userId) { acquire("project-creation:" + userId, limits.projectCreation()); }

    public void reportExport(UUID userId) { acquire("report-export:" + userId, limits.reportExport()); }

    private void acquire(String key, RateLimitProperties.Limit limit) {
        limiter.tryAcquire(key, limit).ifPresent(retryAfter -> { throw new TooManyRequestsException(retryAfter); });
    }
}
