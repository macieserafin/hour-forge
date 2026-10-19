package pl.macieserafin.hourforge.security;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import java.util.regex.Pattern;

/** Redacts valid invitation tokens across messages and exception output, including encoded return URLs. */
public class InvitationTokenRedactor extends CompositeConverter<ILoggingEvent> {
    private static final Pattern TOKEN = Pattern.compile("(?:(?<=%2[Ff])|(?<![A-Za-z0-9_-]))[A-Za-z0-9_-]{43}(?![A-Za-z0-9_-])");

    @Override
    protected String transform(ILoggingEvent event, String text) {
        return TOKEN.matcher(text).replaceAll("[INVITATION_REDACTED]");
    }
}
