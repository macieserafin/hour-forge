package pl.macieserafin.hourforge.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLogFormatterTest {
    private static final String INVITATION_TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde";
    private final JsonLogFormatter formatter = new JsonLogFormatter();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void writesOneJsonObjectPerLineWithContextAndKeyValueFields() {
        var event = event("Saved \"quoted\"\nnext line", null);
        event.setMDCPropertyMap(Map.of("requestId", "caddy-1234-abcd"));
        event.setKeyValuePairs(List.of(new KeyValuePair("event", "team.deleted")));

        var line = formatter.format(event);

        assertThat(line).endsWith("}\n").doesNotContain("\n{");
        var fields = json.readTree(line);
        assertThat(fields.get("level").asString()).isEqualTo("INFO");
        assertThat(fields.get("logger").asString()).isEqualTo("test.logger");
        assertThat(fields.get("message").asString()).isEqualTo("Saved \"quoted\"\nnext line");
        assertThat(fields.get("requestId").asString()).isEqualTo("caddy-1234-abcd");
        assertThat(fields.get("event").asString()).isEqualTo("team.deleted");
        assertThat(fields.get("@timestamp").asString()).endsWith("Z");
    }

    @Test
    void masksInvitationTokensAndOAuthSecretsInMessagesContextAndStackTraces() {
        var event = event("GET /invite/" + INVITATION_TOKEN + "?code=4/0Secret&state=xyz",
                new IllegalStateException("refresh_token=rt-secret Bearer abc.def"));
        event.setMDCPropertyMap(Map.of("path", "/api/invites/" + INVITATION_TOKEN));

        var line = formatter.format(event);

        assertThat(line).doesNotContain(INVITATION_TOKEN, "4/0Secret", "xyz", "rt-secret", "abc.def");
        var fields = json.readTree(line);
        assertThat(fields.get("message").asString()).contains("[INVITATION_REDACTED]", "code=[REDACTED]");
        assertThat(fields.get("stack_trace").asString()).contains("IllegalStateException", "Bearer [REDACTED]");
    }

    private static LoggingEvent event(String message, Throwable error) {
        var logger = new LoggerContext().getLogger("test.logger");
        var event = new LoggingEvent("test", logger, Level.INFO, message, error, null);
        event.setThreadName("main");
        return event;
    }
}
