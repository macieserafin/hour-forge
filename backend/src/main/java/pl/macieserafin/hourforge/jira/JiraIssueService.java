package pl.macieserafin.hourforge.jira;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.security.TooManyRequestsException;
import pl.macieserafin.hourforge.team.TeamMemberRepository;

/**
 * Lists the Jira issues a person can pick for their time. Runs as the person (their own token), so Jira decides what
 * they may see. No database transaction is held while Jira is being called.
 */
@Service
@ConditionalOnProperty(name = "app.jira.enabled", havingValue = "true")
public class JiraIssueService {
    static final int PAGE_SIZE = 20;
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);
    private static final Duration DEFAULT_BACKOFF = Duration.ofSeconds(10);

    private final JiraTokenService tokens;
    private final JiraApiClient api;
    private final JiraConnectionRepository connections;
    private final TeamMemberRepository members;
    private final JiraSearchCache cache;
    private final Clock clock;
    private final Map<String, Instant> cooldowns = new ConcurrentHashMap<>();

    public JiraIssueService(JiraTokenService tokens, JiraApiClient api, JiraConnectionRepository connections,
                            TeamMemberRepository members, JiraSearchCache cache, Clock clock) {
        this.tokens = tokens;
        this.api = api;
        this.connections = connections;
        this.members = members;
        this.cache = cache;
        this.clock = clock;
    }

    public JiraApiClient.SearchResult search(UUID userId, UUID teamId, JiraJql.Query query, String pageToken) {
        var connection = connection(userId, teamId);
        if (pageToken != null && (pageToken.length() > 2000 || pageToken.chars().anyMatch(Character::isISOControl))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page token");
        }
        if (query.projectKey() != null && !query.projectKey().isBlank() && !JiraJql.validProjectKey(query.projectKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid project key");
        }
        var jql = JiraJql.build(query);
        var key = JiraSearchCache.key(userId, connection.getCloudId(), jql, pageToken);
        var cached = cache.get(key);
        if (cached.isPresent()) return cached.get();

        var cooldownKey = userId + ":" + connection.getCloudId();
        var until = cooldowns.get(cooldownKey);
        var now = clock.instant();
        if (until != null) {
            if (until.isAfter(now)) throw new TooManyRequestsException(Duration.between(now, until));
            cooldowns.remove(cooldownKey, until);
        }

        var accessToken = tokens.accessToken(userId, connection.getCloudId());
        try {
            var result = api.search(connection.getCloudId(), accessToken, jql, pageToken, PAGE_SIZE);
            cache.put(key, result);
            return result;
        } catch (JiraApiClient.ApiException exception) {
            throw translate(exception, userId, connection.getCloudId(), cooldownKey);
        }
    }

    public record Found(UUID connectionId, JiraApiClient.Issue issue) {
    }

    /** One issue by id, as the person: used to check a link before it is saved. */
    public Found find(UUID userId, UUID teamId, String issueId) {
        var connection = connection(userId, teamId);
        var cooldownKey = userId + ":" + connection.getCloudId();
        var until = cooldowns.get(cooldownKey);
        var now = clock.instant();
        if (until != null && until.isAfter(now)) throw new TooManyRequestsException(Duration.between(now, until));
        var accessToken = tokens.accessToken(userId, connection.getCloudId());
        try {
            var issue = api.issue(connection.getCloudId(), accessToken, issueId);
            if (issue.id().isBlank() || issue.key().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Jira is not reachable");
            return new Found(connection.getId(), issue);
        } catch (JiraApiClient.ApiException exception) {
            // An issue the person cannot see looks the same as one that does not exist.
            if (exception.status() == 403 || exception.status() == 400) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira issue not found");
            throw translate(exception, userId, connection.getCloudId(), cooldownKey);
        }
    }

    private RuntimeException translate(JiraApiClient.ApiException exception, UUID userId, String cloudId, String cooldownKey) {
        switch (exception.status()) {
            case 429 -> {
                var wait = exception.retryAfterSeconds() > 0 ? Duration.ofSeconds(exception.retryAfterSeconds()) : DEFAULT_BACKOFF;
                if (wait.compareTo(MAX_BACKOFF) > 0) wait = MAX_BACKOFF;
                cooldowns.put(cooldownKey, clock.instant().plus(wait));
                return new TooManyRequestsException(wait);
            }
            case 401 -> {
                // Atlassian no longer accepts the token (access withdrawn); the person has to consent again.
                tokens.forget(userId, cloudId);
                cache.evictUser(userId);
                return new ResponseStatusException(HttpStatus.CONFLICT, "Jira account is not connected");
            }
            case 403 -> {
                return new ResponseStatusException(HttpStatus.FORBIDDEN, "Jira denied access to these issues");
            }
            case 404 -> {
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira site or issue not found");
            }
            case 400 -> {
                return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira rejected this filter");
            }
            default -> {
                return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Jira is not reachable");
            }
        }
    }

    private JiraConnection connection(UUID userId, UUID teamId) {
        members.findByTeamIdAndUserId(teamId, userId).filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return connections.findByTeamId(teamId).filter(JiraConnection::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "The team has no Jira site"));
    }
}
