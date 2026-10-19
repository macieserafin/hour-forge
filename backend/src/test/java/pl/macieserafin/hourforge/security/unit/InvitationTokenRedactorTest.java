package pl.macieserafin.hourforge.security.unit;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;
import pl.macieserafin.hourforge.security.InvitationTokenRedactor;
import static org.assertj.core.api.Assertions.assertThat;

class InvitationTokenRedactorTest {
    @Test
    void redactsTokensInMessagesEncodedReturnUrlsAndStackTraces() {
        String token = "a".repeat(43);
        var context = new LoggerContext();
        try {
            var layout = new PatternLayout();
            layout.setContext(context);
            layout.getInstanceConverterMap().put("redactInvites", InvitationTokenRedactor::new);
            layout.setPattern("%redactInvites(%msg%n%ex){}%nopex");
            layout.start();
            var event = new LoggingEvent();
            event.setMessage("/invite/" + token + " /api/invites/" + token + "/accept /login?returnTo=%2Finvite%2F" + token);
            event.setThrowableProxy(new ThrowableProxy(new IllegalArgumentException("token=" + token)));
            assertThat(layout.doLayout(event)).doesNotContain(token, "%nopex", "PARSER_ERROR")
                    .contains("[INVITATION_REDACTED]").containsOnlyOnce("IllegalArgumentException");
        } finally { context.stop(); }
    }
}
