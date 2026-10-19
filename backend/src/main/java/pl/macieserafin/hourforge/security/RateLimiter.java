package pl.macieserafin.hourforge.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory sliding-window counters. State is per application instance and is lost on restart,
 * which is sufficient for a single-instance deployment.
 */
@Component
public class RateLimiter {
    public static final int MAX_KEYS = 100_000;

    private final Clock clock;
    private final Map<String, Deque<Instant>> events = new HashMap<>();
    private final Map<String, Instant> blockedUntil = new HashMap<>();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Remaining block time for the key, if it is currently blocked. */
    public synchronized Optional<Duration> blocked(String key) {
        var until = blockedUntil.get(key);
        var now = clock.instant();
        if (until == null) return Optional.empty();
        if (!until.isAfter(now)) {
            blockedUntil.remove(key);
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, until));
    }

    /** Records a failure; reaching the limit within the window blocks the key for the block duration. */
    public synchronized boolean recordFailure(String key, RateLimitProperties.Limit limit) {
        var block = Objects.requireNonNull(limit.block(), "Failure limits need a block duration");
        var now = clock.instant();
        var recent = recent(key, limit.window(), now);
        recent.addLast(now);
        if (recent.size() < limit.max()) return false;
        events.remove(key);
        blockedUntil.put(key, now.plus(block));
        return true;
    }

    /** Counts an operation, or returns the time after which it may be retried when the limit is reached. */
    public synchronized Optional<Duration> tryAcquire(String key, RateLimitProperties.Limit limit) {
        var now = clock.instant();
        var recent = recent(key, limit.window(), now);
        if (recent.size() >= limit.max()) {
            return Optional.of(Duration.between(now, recent.peekFirst().plus(limit.window())));
        }
        recent.addLast(now);
        return Optional.empty();
    }

    public synchronized void reset(String key) {
        events.remove(key);
        blockedUntil.remove(key);
    }

    public synchronized void clear() {
        events.clear();
        blockedUntil.clear();
    }

    /** Number of keys currently held in memory (diagnostics and tests). */
    public synchronized int trackedKeys() {
        return events.size() + blockedUntil.size();
    }

    private Deque<Instant> recent(String key, Duration window, Instant now) {
        if (!events.containsKey(key) && events.size() >= MAX_KEYS) evict(now, window);
        var recent = events.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(now.minus(window))) recent.removeFirst();
        return recent;
    }

    private void evict(Instant now, Duration window) {
        // Bound memory against many spoofed keys; when everything is recent, start over rather than grow.
        events.values().removeIf(recent -> recent.isEmpty() || !recent.peekLast().isAfter(now.minus(window)));
        blockedUntil.values().removeIf(until -> !until.isAfter(now));
        if (events.size() >= MAX_KEYS) events.clear();
    }
}
