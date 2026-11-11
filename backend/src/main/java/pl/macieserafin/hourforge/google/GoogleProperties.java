package pl.macieserafin.hourforge.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Settings of the sign-in with Google. The client id switches it on; the secret comes from the environment only. The
 * issuer is Google's in production and is overridable solely for tests and the end-to-end stand-in.
 */
@ConfigurationProperties("app.google")
public record GoogleProperties(String clientId, String clientSecret, String redirectUri, String issuerUri) {
    public static final String GOOGLE_ISSUER = "https://accounts.google.com";

    public GoogleProperties {
        clientId = blank(clientId) ? null : clientId.strip();
        clientSecret = blank(clientSecret) ? null : clientSecret.strip();
        redirectUri = blank(redirectUri) ? null : redirectUri.strip();
        issuerUri = blank(issuerUri) ? GOOGLE_ISSUER : stripSlash(issuerUri);
    }

    /** On as soon as a client id is set; everything else is then checked by {@link #requireComplete()}. */
    public boolean enabled() {
        return clientId != null;
    }

    /** Stops the start-up when the integration is on but cannot work. */
    public void requireComplete() {
        if (!enabled()) return;
        if (clientSecret == null) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID is set but GOOGLE_CLIENT_SECRET is missing: set both to turn on "
                    + "the sign-in with Google, or leave GOOGLE_CLIENT_ID empty to keep it off");
        }
        if (redirectUri != null && !validRedirect(redirectUri)) {
            throw new IllegalStateException("GOOGLE_REDIRECT_URI must be an absolute http(s) address ending with "
                    + GoogleSignIn.CALLBACK_PATH + ", for example http://localhost:5173" + GoogleSignIn.CALLBACK_PATH);
        }
    }

    public boolean usesGoogle() {
        return GOOGLE_ISSUER.equals(issuerUri);
    }

    private static boolean validRedirect(String value) {
        try {
            var uri = new URI(value);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getRawUserInfo() == null
                    && GoogleSignIn.CALLBACK_PATH.equals(uri.getPath());
        } catch (URISyntaxException exception) {
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
        return "GoogleProperties[clientId=" + clientId + ", redirectUri=" + redirectUri + ", issuerUri=" + issuerUri + ", secret=redacted]";
    }
}
