package pl.macieserafin.hourforge.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** Throttles failed logins per account and address, per address and per account. */
@Service
public class LoginAttemptService {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final RateLimiter limiter;
    private final RateLimitProperties limits;

    public LoginAttemptService(RateLimiter limiter, RateLimitProperties limits) {
        this.limiter = limiter;
        this.limits = limits;
    }

    public Optional<Duration> blockedFor(String email, String ip) {
        var account = normalize(email);
        return Stream.of(accountIpKey(account, ip), ipKey(ip), accountKey(account))
                .map(limiter::blocked).flatMap(Optional::stream).max(Duration::compareTo);
    }

    public void recordFailure(String email, String ip) {
        var account = normalize(email);
        log.info("Failed login: email={}, ip={}", printable(account), printable(ip));
        boolean blocked = limiter.recordFailure(accountIpKey(account, ip), limits.loginAccountIp());
        blocked |= limiter.recordFailure(ipKey(ip), limits.loginIp());
        blocked |= limiter.recordFailure(accountKey(account), limits.loginAccount());
        if (blocked) {
            log.warn("Login temporarily blocked after repeated failures: email={}, ip={}", printable(account), printable(ip));
        }
    }

    public void recordSuccess(String email, String ip) {
        limiter.reset(accountIpKey(normalize(email), ip));
    }

    private static String accountIpKey(String account, String ip) { return "login:account-ip:" + account + "|" + ip; }

    private static String ipKey(String ip) { return "login:ip:" + ip; }

    private static String accountKey(String account) { return "login:account:" + account; }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    // Prevents log forging through line breaks and oversized values in user-supplied input.
    private static String printable(String value) {
        var safe = value == null ? "" : value.replaceAll("\\p{Cntrl}", "_");
        return safe.length() > 255 ? safe.substring(0, 255) + "…" : safe;
    }
}
