package pl.macieserafin.hourforge.security;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

import java.util.regex.Pattern;

/**
 * Redacts OAuth and OpenID Connect secrets that could reach a log line: the authorization code, state, nonce and PKCE
 * values of a callback address, tokens in query strings or JSON, and bearer tokens. A second line of defence; the code
 * itself never logs them.
 */
public class OAuthSecretRedactor extends CompositeConverter<ILoggingEvent> {
    private static final Pattern PARAMETER = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])(code|state|nonce|code_verifier|code_challenge|id_token|access_token|refresh_token|client_secret)=[^&\\s\"'<>]+");
    private static final Pattern JSON = Pattern.compile(
            "(?i)\"(code|state|nonce|code_verifier|id_token|access_token|refresh_token|client_secret)\"\\s*:\\s*\"[^\"]*\"");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*");

    @Override
    protected String transform(ILoggingEvent event, String text) {
        return redact(text);
    }

    public static String redact(String text) {
        var redacted = PARAMETER.matcher(text).replaceAll("$1=[REDACTED]");
        redacted = JSON.matcher(redacted).replaceAll("\"$1\":\"[REDACTED]\"");
        return BEARER.matcher(redacted).replaceAll("Bearer [REDACTED]");
    }
}
