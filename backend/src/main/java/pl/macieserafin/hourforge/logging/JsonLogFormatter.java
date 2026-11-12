package pl.macieserafin.hourforge.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import pl.macieserafin.hourforge.security.InvitationTokenRedactor;
import pl.macieserafin.hourforge.security.OAuthSecretRedactor;

/**
 * One JSON object per line for the production log (profile {@code prod}, see logback-spring.xml). Message, stack trace
 * and context values go through the same redaction as the text log, so invitation tokens and OAuth secrets that reach
 * a log line are masked here too. Context (MDC, e.g. {@code requestId}) and key-value pairs become top-level fields.
 */
public class JsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

    @Override
    public String format(ILoggingEvent event) {
        var json = new StringBuilder(256).append('{');
        field(json, "@timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        field(json, "level", event.getLevel().toString());
        field(json, "logger", event.getLoggerName());
        field(json, "thread", event.getThreadName());
        field(json, "message", redact(event.getFormattedMessage()));
        Map<String, String> context = event.getMDCPropertyMap();
        if (context != null) context.forEach((key, value) -> field(json, key, redact(value)));
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs().forEach(pair -> field(json, pair.key, redact(String.valueOf(pair.value))));
        }
        if (event.getThrowableProxy() != null) {
            field(json, "stack_trace", redact(ThrowableProxyUtil.asString(event.getThrowableProxy())));
        }
        return json.append("}\n").toString();
    }

    static String redact(String text) {
        return text == null ? null : OAuthSecretRedactor.redact(InvitationTokenRedactor.redact(text));
    }

    private static void field(StringBuilder json, String name, String value) {
        if (value == null) return;
        if (json.length() > 1) json.append(',');
        quote(json, name).append(':');
        quote(json, value);
    }

    private static StringBuilder quote(StringBuilder json, String text) {
        json.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) json.append(String.format("\\u%04x", (int) c));
                    else json.append(c);
                }
            }
        }
        return json.append('"');
    }
}
