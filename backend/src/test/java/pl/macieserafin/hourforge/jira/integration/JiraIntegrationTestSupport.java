package pl.macieserafin.hourforge.jira.integration;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;

/** Runs the application with the Jira integration switched on, against a fake Atlassian. */
public abstract class JiraIntegrationTestSupport extends ApiIntegrationTestSupport {
    protected static final String CLIENT_SECRET = "test-client-secret-value";
    protected static final FakeAtlassian ATLASSIAN = new FakeAtlassian();
    private static final String KEY = key();
    protected static final String OLD_KEY = key();

    private static String key() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @DynamicPropertySource
    static void jira(DynamicPropertyRegistry registry) {
        registry.add("app.jira.enabled", () -> "true");
        registry.add("app.jira.client-id", () -> "test-client-id");
        registry.add("app.jira.client-secret", () -> CLIENT_SECRET);
        registry.add("app.jira.redirect-uri", () -> "http://localhost:8080/api/integrations/jira/callback");
        registry.add("app.jira.encryption-key", () -> KEY);
        registry.add("app.jira.previous-encryption-keys.v0", () -> OLD_KEY);
        registry.add("app.jira.auth-base-url", ATLASSIAN::baseUrl);
        registry.add("app.jira.api-base-url", ATLASSIAN::baseUrl);
    }

    @BeforeEach
    void resetAtlassian() {
        ATLASSIAN.reset();
    }
}
