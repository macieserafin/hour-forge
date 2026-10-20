package pl.macieserafin.hourforge.jira;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The only code that talks to Atlassian's OAuth and accessible-resources endpoints. Hosts come from configuration
 * (api.atlassian.com and auth.atlassian.com), never from user input. Errors are reduced to a status on purpose: the
 * original exception text can contain response bodies, which must not reach the logs.
 */
@Component
@ConditionalOnProperty(name = "app.jira.enabled", havingValue = "true")
public class JiraAtlassianClient {
    private static final Logger log = LoggerFactory.getLogger(JiraAtlassianClient.class);

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds, String scope) {
        @Override
        public String toString() {
            return "TokenResponse[redacted]";
        }
    }

    public record Site(String id, String url, String name) {
    }

    /** Atlassian could not be reached or answered with an error that is not about the grant. */
    public static class UpstreamException extends RuntimeException {
        private final int status;

        public UpstreamException(int status) {
            super("Atlassian request failed", null, false, false);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /** The refresh token or authorization code was refused: the user must consent again. */
    public static class GrantRejectedException extends RuntimeException {
        public GrantRejectedException() {
            super("Atlassian rejected the grant", null, false, false);
        }
    }

    private final JiraProperties properties;
    private final RestClient client;

    public JiraAtlassianClient(JiraProperties properties) {
        this.properties = properties;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public String authorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(properties.authBaseUrl() + "/authorize")
                .queryParam("audience", "api.atlassian.com")
                .queryParam("client_id", properties.clientId())
                .queryParam("scope", properties.scopes())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("state", state)
                .queryParam("response_type", "code")
                .queryParam("prompt", "consent")
                .build().encode().toUriString();
    }

    public TokenResponse exchangeCode(String code) {
        var body = new LinkedHashMap<String, String>();
        body.put("grant_type", "authorization_code");
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        body.put("code", code);
        body.put("redirect_uri", properties.redirectUri());
        return token(body);
    }

    public TokenResponse refresh(String refreshToken) {
        var body = new LinkedHashMap<String, String>();
        body.put("grant_type", "refresh_token");
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        body.put("refresh_token", refreshToken);
        return token(body);
    }

    public List<Site> accessibleSites(String accessToken) {
        try {
            var resources = client.get().uri(properties.apiBaseUrl() + "/oauth/token/accessible-resources")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
            if (resources == null) return List.of();
            return resources.stream()
                    .map(resource -> new Site(text(resource.get("id")), text(resource.get("url")), text(resource.get("name"))))
                    .filter(site -> !site.id().isBlank() && site.url().startsWith("https://"))
                    .toList();
        } catch (RestClientResponseException exception) {
            log.warn("Atlassian accessible-resources answered {}", exception.getStatusCode().value());
            throw new UpstreamException(exception.getStatusCode().value());
        } catch (RuntimeException exception) {
            log.warn("Atlassian accessible-resources could not be read ({})", exception.getClass().getSimpleName());
            throw new UpstreamException(0);
        }
    }

    private TokenResponse token(Map<String, String> request) {
        try {
            var response = client.post().uri(properties.authBaseUrl() + "/oauth/token")
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(request)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (response == null || text(response.get("access_token")).isBlank()) throw new UpstreamException(0);
            var expires = response.get("expires_in") instanceof Number number ? number.longValue() : 3600L;
            var refresh = text(response.get("refresh_token"));
            return new TokenResponse(text(response.get("access_token")), refresh.isBlank() ? null : refresh, expires,
                    text(response.get("scope")));
        } catch (RestClientResponseException exception) {
            var status = exception.getStatusCode().value();
            log.warn("Atlassian token endpoint answered {}", status);
            if (status == 400 || status == 401 || status == 403) throw new GrantRejectedException();
            throw new UpstreamException(status);
        } catch (UpstreamException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Atlassian token endpoint could not be reached ({})", exception.getClass().getSimpleName());
            throw new UpstreamException(0);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
