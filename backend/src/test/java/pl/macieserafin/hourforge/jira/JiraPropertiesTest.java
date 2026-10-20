package pl.macieserafin.hourforge.jira;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JiraPropertiesTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static JiraProperties properties(String id, String secret, String redirect, String key) {
        return new JiraProperties(true, id, secret, redirect, null, null, null, null, key, null);
    }

    @Test
    void appliesAtlassianDefaults() {
        var properties = properties("id", "secret", "http://localhost/cb", KEY);
        assertThat(properties.scopes()).isEqualTo("read:jira-work offline_access");
        assertThat(properties.authBaseUrl()).isEqualTo("https://auth.atlassian.com");
        assertThat(properties.apiBaseUrl()).isEqualTo("https://api.atlassian.com");
        assertThat(properties.problems()).isEmpty();
    }

    @Test
    void reportsEveryMissingSettingByNameWithoutValues() {
        var properties = properties("", " ", null, "not-base64!");
        assertThat(properties.problems()).hasSize(4);
        assertThatThrownBy(properties::requireComplete).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JIRA_CLIENT_ID").hasMessageContaining("JIRA_TOKEN_ENCRYPTION_KEY");
    }

    @Test
    void rejectsAKeyOfTheWrongLength() {
        var shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThat(properties("id", "secret", "http://localhost/cb", shortKey).problems()).hasSize(1);
    }

    @Test
    void doesNotPrintSecrets() {
        var text = new JiraProperties(true, "client", "super-secret", "http://localhost/cb", null, null, null, "v1", KEY,
                Map.of()).toString();
        assertThat(text).doesNotContain("super-secret").doesNotContain(KEY);
    }
}
