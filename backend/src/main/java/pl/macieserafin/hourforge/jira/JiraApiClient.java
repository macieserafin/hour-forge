package pl.macieserafin.hourforge.jira;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads issues from Jira Cloud through api.atlassian.com. The host is configuration, the site is addressed only by
 * its cloud id (checked to be a plain identifier), and responses are reduced to the few fields the app shows.
 */
@Component
@ConditionalOnProperty(name = "app.jira.enabled", havingValue = "true")
public class JiraApiClient {
    private static final Logger log = LoggerFactory.getLogger(JiraApiClient.class);
    private static final Pattern ISSUE_ID = Pattern.compile("[0-9]{1,20}");
    private static final Pattern CLOUD_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    public record Issue(String id, String key, String summary, String status, String issueType, String projectKey) {
    }

    public record SearchResult(List<Issue> issues, String nextPageToken) {
    }

    /** Jira answered with an error; {@code retryAfterSeconds} is set for 429. */
    public static class ApiException extends RuntimeException {
        private final int status;
        private final long retryAfterSeconds;

        public ApiException(int status, long retryAfterSeconds) {
            super("Jira request failed", null, false, false);
            this.status = status;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int status() {
            return status;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    private final JiraProperties properties;
    private final RestClient client;

    public JiraApiClient(JiraProperties properties) {
        this.properties = properties;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public SearchResult search(String cloudId, String accessToken, String jql, String nextPageToken, int maxResults) {
        var body = new LinkedHashMap<String, Object>();
        body.put("jql", jql);
        body.put("maxResults", maxResults);
        body.put("fields", List.of("summary", "status", "issuetype", "project"));
        if (nextPageToken != null && !nextPageToken.isBlank()) body.put("nextPageToken", nextPageToken);
        var base = site(cloudId);
        var response = call(() -> client.post().uri(base + "/rest/api/3/search/jql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { }));
        var issues = new java.util.ArrayList<Issue>();
        if (response != null && response.get("issues") instanceof List<?> raw) {
            for (var item : raw) {
                if (item instanceof Map<?, ?> map) issues.add(issue(map));
            }
        }
        var next = response == null ? null : text(response.get("nextPageToken"));
        return new SearchResult(List.copyOf(issues), next == null || next.isBlank() ? null : next);
    }

    public Issue issue(String cloudId, String accessToken, String issueId) {
        if (issueId == null || !ISSUE_ID.matcher(issueId).matches()) throw new IllegalArgumentException("Invalid issue id");
        var base = site(cloudId);
        var response = call(() -> client.get().uri(base + "/rest/api/3/issue/" + issueId + "?fields=summary,status,issuetype,project")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).accept(MediaType.APPLICATION_JSON)
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { }));
        if (response == null) throw new ApiException(0, 0);
        return issue(response);
    }

    private String site(String cloudId) {
        if (cloudId == null || !CLOUD_ID.matcher(cloudId).matches()) throw new IllegalArgumentException("Invalid cloud id");
        return properties.apiBaseUrl() + "/ex/jira/" + cloudId;
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            long retryAfter = 0;
            if (status == 429) {
                try {
                    retryAfter = Long.parseLong(String.valueOf(exception.getResponseHeaders() == null ? "" : exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)).strip());
                } catch (NumberFormatException ignored) {
                    retryAfter = 0;
                }
            }
            log.warn("Jira answered {}", status);
            throw new ApiException(status, retryAfter);
        } catch (RuntimeException exception) {
            log.warn("Jira could not be reached ({})", exception.getClass().getSimpleName());
            throw new ApiException(0, 0);
        }
    }

    private static Issue issue(Map<?, ?> map) {
        var fields = map.get("fields") instanceof Map<?, ?> f ? f : Map.of();
        return new Issue(text(map.get("id")), text(map.get("key")), text(fields.get("summary")), name(fields.get("status")),
                name(fields.get("issuetype")), key(fields.get("project")));
    }

    private static String name(Object value) {
        return value instanceof Map<?, ?> map ? text(map.get("name")) : "";
    }

    private static String key(Object value) {
        return value instanceof Map<?, ?> map ? text(map.get("key")) : "";
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
