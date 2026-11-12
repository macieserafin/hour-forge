package pl.macieserafin.hourforge.account;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.macieserafin.hourforge.security.RateLimitProperties;
import pl.macieserafin.hourforge.security.RateLimiter;
import pl.macieserafin.hourforge.security.TooManyRequestsException;

/**
 * Admission to a copy of the person's data: one at a time per person and at most {@code app.data-export.max-concurrent}
 * at once in this instance (each holds a database connection and a request thread while it streams), plus the hourly
 * limit per person. A refusal is a 429 with Retry-After before anything is written; a refusal for lack of a free slot
 * does not use up the hourly limit.
 */
@Component
public class ExportSlots {
    static final Duration RETRY_WHEN_BUSY = Duration.ofSeconds(30);

    private final Semaphore instance;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    private final RateLimiter limiter;
    private final RateLimitProperties limits;

    public ExportSlots(@Value("${app.data-export.max-concurrent:2}") int maxConcurrent, RateLimiter limiter,
                       RateLimitProperties limits) {
        if (maxConcurrent < 1) throw new IllegalArgumentException("app.data-export.max-concurrent must be at least 1");
        this.instance = new Semaphore(maxConcurrent);
        this.limiter = limiter;
        this.limits = limits;
    }

    /** Takes a slot for the person; close it when the copy has been written (or failed). */
    public Slot open(UUID userId) {
        if (!running.add(userId)) throw new TooManyRequestsException(RETRY_WHEN_BUSY);
        if (!instance.tryAcquire()) {
            running.remove(userId);
            throw new TooManyRequestsException(RETRY_WHEN_BUSY);
        }
        var slot = new Slot(this, userId);
        try {
            limiter.tryAcquire("data-export:" + userId, limits.dataExport())
                    .ifPresent(retryAfter -> { throw new TooManyRequestsException(retryAfter); });
        } catch (RuntimeException refused) {
            slot.close();
            throw refused;
        }
        return slot;
    }

    public static final class Slot implements AutoCloseable {
        private final ExportSlots slots;
        private final UUID userId;
        private boolean closed;

        private Slot(ExportSlots slots, UUID userId) {
            this.slots = slots;
            this.userId = userId;
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            slots.running.remove(userId);
            slots.instance.release();
        }
    }
}
