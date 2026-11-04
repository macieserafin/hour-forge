package pl.macieserafin.hourforge.google;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What the person asked for when leaving for Google: sign in (or sign up) or, from the account page, link Google
 * (A13 K3). It travels inside the stored authorization request, so it comes back only with a matching {@code state}.
 * Only strings are stored, which keeps sessions saved by Spring Session readable across versions.
 */
public record GoogleIntent(Mode mode, boolean remembered, String returnTo) {
    public enum Mode {
        LOGIN, LINK;

        /** {@code null} for an unknown mode; a missing mode is a sign-in. */
        static Mode parse(String value) {
            if (value == null || value.isEmpty()) return LOGIN;
            return switch (value) {
                case "login" -> LOGIN;
                case "link" -> LINK;
                default -> null;
            };
        }
    }

    public static final String DEFAULT_RETURN_TO = "/dashboard";
    static final String MODE = GoogleIntent.class.getName() + ".MODE";
    static final String REMEMBERED = GoogleIntent.class.getName() + ".REMEMBERED";
    static final String RETURN_TO = GoogleIntent.class.getName() + ".RETURN_TO";
    /** The request attribute that carries the intent from the removed authorization request to the handlers. */
    static final String CURRENT = GoogleIntent.class.getName() + ".CURRENT";

    private static final int MAX_RETURN_TO = 512;
    private static final Pattern INVITATION = Pattern.compile("/invite/[A-Za-z0-9_-]{1,255}");
    private static final Pattern QUERY = Pattern.compile("[A-Za-z0-9_.~%=&+-]*");
    private static final java.util.Set<String> PAGES = java.util.Set.of("/dashboard", "/teams", "/projects", "/time", "/integrations", "/whats-new");

    static GoogleIntent fromStart(HttpServletRequest request) {
        var mode = Mode.parse(request.getParameter("mode"));
        return new GoogleIntent(mode == null ? Mode.LOGIN : mode, "true".equals(request.getParameter("rememberMe")),
                safeReturnTo(request.getParameter("returnTo")));
    }

    static GoogleIntent from(OAuth2AuthorizationRequest request) {
        String mode = request.getAttribute(MODE);
        String remembered = request.getAttribute(REMEMBERED);
        String returnTo = request.getAttribute(RETURN_TO);
        var parsed = Mode.parse(mode == null ? null : mode.toLowerCase(Locale.ROOT));
        return new GoogleIntent(parsed == null ? Mode.LOGIN : parsed, "true".equals(remembered), safeReturnTo(returnTo));
    }

    /** The intent of the authorization request this callback answered, or a plain sign-in when there was none. */
    static GoogleIntent current(HttpServletRequest request) {
        return request.getAttribute(CURRENT) instanceof GoogleIntent intent ? intent : new GoogleIntent(Mode.LOGIN, false, DEFAULT_RETURN_TO);
    }

    void storeIn(OAuth2AuthorizationRequest.Builder builder) {
        builder.attributes(attributes -> {
            attributes.put(MODE, mode.name());
            attributes.put(REMEMBERED, Boolean.toString(remembered));
            attributes.put(RETURN_TO, returnTo);
        });
    }

    /**
     * The server-side twin of the frontend's {@code safeReturnTo}: only the app's own pages and invitations, as a path
     * with an optional plain query. Anything else (other hosts, {@code //}, backslashes, fragments) becomes the dashboard.
     */
    public static String safeReturnTo(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_RETURN_TO) return DEFAULT_RETURN_TO;
        var queryAt = value.indexOf('?');
        var path = queryAt < 0 ? value : value.substring(0, queryAt);
        var query = queryAt < 0 ? null : value.substring(queryAt + 1);
        if (!(PAGES.contains(path) || INVITATION.matcher(path).matches())) return DEFAULT_RETURN_TO;
        if (query != null && !QUERY.matcher(query).matches()) return DEFAULT_RETURN_TO;
        return query == null || query.isEmpty() ? path : path + "?" + query;
    }
}
