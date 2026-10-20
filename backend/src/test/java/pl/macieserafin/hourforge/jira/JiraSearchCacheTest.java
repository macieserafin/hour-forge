package pl.macieserafin.hourforge.jira;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JiraSearchCacheTest {
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-20T10:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final JiraSearchCache cache = new JiraSearchCache(clock);
    private final JiraApiClient.SearchResult result = new JiraApiClient.SearchResult(List.of(), null);

    @Test
    void entriesExpireAfterTheTimeToLive() {
        var key = JiraSearchCache.key(UUID.randomUUID(), "cloud", "jql", null);
        cache.put(key, result);
        assertThat(cache.get(key)).contains(result);
        clock.now = clock.now.plus(JiraSearchCache.TTL).plusSeconds(1);
        assertThat(cache.get(key)).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void oneNeverSeesAnotherPersonsEntries() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        cache.put(JiraSearchCache.key(first, "cloud", "jql", null), result);
        assertThat(cache.get(JiraSearchCache.key(second, "cloud", "jql", null))).isEmpty();
    }

    @Test
    void evictingAPersonLeavesOthersAlone() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        cache.put(JiraSearchCache.key(first, "cloud", "a", null), result);
        cache.put(JiraSearchCache.key(first, "cloud", "b", null), result);
        cache.put(JiraSearchCache.key(second, "cloud", "a", null), result);
        cache.evictUser(first);
        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.get(JiraSearchCache.key(second, "cloud", "a", null))).isPresent();
    }

    @Test
    void theSizeIsBounded() {
        var user = UUID.randomUUID();
        for (int i = 0; i < JiraSearchCache.MAX_ENTRIES + 50; i++) cache.put(JiraSearchCache.key(user, "cloud", "q" + i, null), result);
        assertThat(cache.size()).isEqualTo(JiraSearchCache.MAX_ENTRIES);
    }
}
