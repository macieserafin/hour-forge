package pl.macieserafin.hourforge.security.unit;

import org.junit.jupiter.api.Test;
import pl.macieserafin.hourforge.security.RateLimitProperties.Limit;
import pl.macieserafin.hourforge.security.RateLimiter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {
    private static final Limit FAILURES = new Limit(3, Duration.ofMinutes(15), Duration.ofMinutes(10));
    private static final Limit OPERATIONS = new Limit(2, Duration.ofHours(1), null);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void blocksAfterTheFailureLimitUntilTheBlockExpires() {
        assertThat(limiter.recordFailure("key", FAILURES)).isFalse();
        assertThat(limiter.recordFailure("key", FAILURES)).isFalse();
        assertThat(limiter.blocked("key")).isEmpty();
        assertThat(limiter.recordFailure("key", FAILURES)).isTrue();
        assertThat(limiter.blocked("key")).contains(Duration.ofMinutes(10));
        assertThat(limiter.blocked("other")).isEmpty();

        clock.advance(Duration.ofMinutes(9));
        assertThat(limiter.blocked("key")).contains(Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.blocked("key")).isEmpty();
        assertThat(limiter.recordFailure("key", FAILURES)).isFalse();
    }

    @Test
    void failuresOutsideTheWindowAreForgotten() {
        limiter.recordFailure("key", FAILURES);
        limiter.recordFailure("key", FAILURES);
        clock.advance(Duration.ofMinutes(15));
        assertThat(limiter.recordFailure("key", FAILURES)).isFalse();
        assertThat(limiter.blocked("key")).isEmpty();
    }

    @Test
    void resetClearsFailuresAndBlocks() {
        for (int i = 0; i < 3; i++) limiter.recordFailure("key", FAILURES);
        limiter.reset("key");
        assertThat(limiter.blocked("key")).isEmpty();
        assertThat(limiter.recordFailure("key", FAILURES)).isFalse();
    }

    @Test
    void operationsAreRefusedUntilTheOldestLeavesTheWindow() {
        assertThat(limiter.tryAcquire("key", OPERATIONS)).isEmpty();
        clock.advance(Duration.ofMinutes(20));
        assertThat(limiter.tryAcquire("key", OPERATIONS)).isEmpty();
        assertThat(limiter.tryAcquire("key", OPERATIONS)).contains(Duration.ofMinutes(40));
        assertThat(limiter.tryAcquire("other", OPERATIONS)).isEmpty();
        clock.advance(Duration.ofMinutes(40));
        assertThat(limiter.tryAcquire("key", OPERATIONS)).isEmpty();
    }

    @Test
    void boundsMemoryByEvictingExpiredKeysAndStartingOverWhenAllAreRecent() {
        for (int i = 0; i < RateLimiter.MAX_KEYS; i++) limiter.tryAcquire("old-" + i, OPERATIONS);
        clock.advance(Duration.ofHours(1));
        limiter.tryAcquire("new", OPERATIONS);
        assertThat(limiter.trackedKeys()).isEqualTo(1);

        for (int i = 1; i < RateLimiter.MAX_KEYS; i++) limiter.tryAcquire("recent-" + i, OPERATIONS);
        limiter.tryAcquire("overflow", OPERATIONS);
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    @Test
    void failureLimitsRequireABlockAndLimitsRejectInvalidValues() {
        assertThatThrownBy(() -> limiter.recordFailure("key", OPERATIONS)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Limit(0, Duration.ofMinutes(1), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Limit(1, Duration.ZERO, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Limit(1, Duration.ofMinutes(1), Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) { this.now = now; }

        void advance(Duration duration) { now = now.plus(duration); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }
}
