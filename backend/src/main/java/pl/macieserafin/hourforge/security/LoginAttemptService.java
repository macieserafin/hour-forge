package pl.macieserafin.hourforge.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.macieserafin.hourforge.user.EmailAddress;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

/** Throttles failed logins per account and address, per address and per account. */
@Service
public class LoginAttemptService {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final RateLimiter limiter;
    private final RateLimitProperties limits;
    private final UserRepository users;

    public LoginAttemptService(RateLimiter limiter, RateLimitProperties limits, UserRepository users) {
        this.limiter = limiter;
        this.limits = limits;
        this.users = users;
    }

    public Optional<Duration> blockedFor(String email, String ip) {
        var account = accountIdentity(email);
        return Stream.of(accountIpKey(account, ip), ipKey(ip), accountKey(account))
                .map(limiter::blocked).flatMap(Optional::stream).max(Duration::compareTo);
    }

    public void recordFailure(String email, String ip) {
        var account = accountIdentity(email);
        log.info("Failed login: email={}, ip={}", printable(normalize(email)), printable(ip));
        boolean blocked = limiter.recordFailure(accountIpKey(account, ip), limits.loginAccountIp());
        blocked |= limiter.recordFailure(ipKey(ip), limits.loginIp());
        blocked |= limiter.recordFailure(accountKey(account), limits.loginAccount());
        if (blocked) {
            log.warn("Login temporarily blocked after repeated failures: email={}, ip={}", printable(normalize(email)), printable(ip));
        }
    }

    public void recordSuccess(String email, String ip) {
        limiter.reset(accountIpKey(accountIdentity(email), ip));
    }

    private static String accountIpKey(String account, String ip) { return "login:account-ip:" + account + "|" + ip; }

    private static String ipKey(String ip) { return "login:ip:" + ip; }

    private static String accountKey(String account) { return "login:account:" + account; }

    private static String normalize(String email) {
        return email == null ? "" : EmailAddress.normalize(email);
    }

    private String accountIdentity(String email) {
        var normalized = normalize(email);
        // The same database comparison identifies the account for both authentication and throttling.
        // Separate prefixes keep arbitrary input from colliding with a user's id.
        return users.findByEmailIgnoreCase(normalized)
                .map(user -> "user:" + user.getId())
                .orElse("email:" + normalized);
    }

    // Prevents log forging through line breaks and oversized values in user-supplied input.
    private static String printable(String value) {
        var safe = value == null ? "" : value.replaceAll("\\p{Cntrl}", "_");
        return safe.length() > 255 ? safe.substring(0, 255) + "…" : safe;
    }
}
