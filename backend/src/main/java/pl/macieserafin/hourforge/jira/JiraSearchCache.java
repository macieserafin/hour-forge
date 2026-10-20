package pl.macieserafin.hourforge.jira;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A short-lived, size-bounded memory of search results. Keys start with the person's id, so one person's results are
 * never served to another, and a disconnect removes that person's entries at once.
 */
@Component
@ConditionalOnProperty(name = "app.jira.enabled", havingValue = "true")
public class JiraSearchCache {
    static final Duration TTL = Duration.ofSeconds(30);
    static final int MAX_ENTRIES = 300;

    private record Entry(JiraApiClient.SearchResult result, Instant expiresAt) {
    }

    private final Clock clock;
    private final Map<String, Entry> entries = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public JiraSearchCache(Clock clock) {
        this.clock = clock;
    }

    public static String key(UUID userId, String cloudId, String jql, String pageToken) {
        return userId + "|" + cloudId + "|" + jql + "|" + (pageToken == null ? "" : pageToken);
    }

    public synchronized Optional<JiraApiClient.SearchResult> get(String key) {
        var entry = entries.get(key);
        if (entry == null) return Optional.empty();
        if (!entry.expiresAt().isAfter(clock.instant())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    public synchronized void put(String key, JiraApiClient.SearchResult result) {
        entries.put(key, new Entry(result, clock.instant().plus(TTL)));
    }

    public synchronized void evictUser(UUID userId) {
        entries.keySet().removeIf(key -> key.startsWith(userId + "|"));
    }

    public synchronized void clear() {
        entries.clear();
    }

    synchronized int size() {
        return entries.size();
    }
}
