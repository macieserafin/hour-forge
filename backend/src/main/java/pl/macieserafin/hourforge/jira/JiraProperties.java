package pl.macieserafin.hourforge.jira;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the Jira Cloud integration. The OAuth client secret and the encryption keys come from the environment
 * only. The two base URLs are fixed to Atlassian in production and are overridable solely for tests.
 */
@ConfigurationProperties("app.jira")
public record JiraProperties(boolean enabled, String clientId, String clientSecret, String redirectUri, String scopes,
                             String authBaseUrl, String apiBaseUrl, String encryptionKeyVersion, String encryptionKey,
                             Map<String, String> previousEncryptionKeys) {
    public static final String DEFAULT_SCOPES = "read:jira-work offline_access";

    public JiraProperties {
        scopes = blank(scopes) ? DEFAULT_SCOPES : scopes.strip();
        authBaseUrl = blank(authBaseUrl) ? "https://auth.atlassian.com" : stripSlash(authBaseUrl);
        apiBaseUrl = blank(apiBaseUrl) ? "https://api.atlassian.com" : stripSlash(apiBaseUrl);
        encryptionKeyVersion = blank(encryptionKeyVersion) ? "v1" : encryptionKeyVersion.strip();
        previousEncryptionKeys = previousEncryptionKeys == null ? Map.of() : Map.copyOf(previousEncryptionKeys);
    }

    /** What is missing or malformed when the integration is switched on; empty when everything is in place. */
    public List<String> problems() {
        var problems = new ArrayList<String>();
        if (blank(clientId)) problems.add("JIRA_CLIENT_ID");
        if (blank(clientSecret)) problems.add("JIRA_CLIENT_SECRET");
        if (blank(redirectUri)) problems.add("JIRA_REDIRECT_URI");
        if (!validKey(encryptionKey)) problems.add("JIRA_TOKEN_ENCRYPTION_KEY (32 bytes, base64)");
        previousEncryptionKeys.forEach((version, key) -> {
            if (!validKey(key)) problems.add("previous encryption key " + version + " (32 bytes, base64)");
        });
        return problems;
    }

    public void requireComplete() {
        var problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("JIRA_INTEGRATION_ENABLED is on but these settings are missing or invalid: "
                    + String.join(", ", problems));
        }
    }

    private static boolean validKey(String key) {
        if (blank(key)) return false;
        try {
            return Base64.getDecoder().decode(key.strip()).length == 32;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String stripSlash(String url) {
        var text = url.strip();
        return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }

    @Override
    public String toString() {
        return "JiraProperties[enabled=" + enabled + ", clientId=" + clientId + ", redirectUri=" + redirectUri + ", secrets=redacted]";
    }
}
