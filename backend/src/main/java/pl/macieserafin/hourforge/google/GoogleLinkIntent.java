package pl.macieserafin.hourforge.google;

import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Permission to link Google to the signed-in account, given by {@code POST /api/account/google/link} after the password.
 * It lives in that session only, for ten minutes, and is used once: the return from Google takes it, whatever
 * the outcome. The credential version it was given for must still be current, so a password change, an unlink or
 * "sign out everywhere" in between voids it.
 */
public record GoogleLinkIntent(UUID userId, int sessionVersion, Instant expiresAt) implements Serializable {
    public static final Duration VALID_FOR = Duration.ofMinutes(10);
    static final String ATTRIBUTE = GoogleLinkIntent.class.getName();

    public static void store(HttpServletRequest request, UUID userId, int sessionVersion, Instant now) {
        request.getSession().setAttribute(ATTRIBUTE, new GoogleLinkIntent(userId, sessionVersion, now.plus(VALID_FOR)));
    }

    /** Where Spring keeps the authorization request that waits for Google's answer (HttpSessionOAuth2AuthorizationRequestRepository). */
    private static final String WAITING_AUTHORIZATION_REQUEST =
            "org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST";

    /**
     * Called at every new sign-in in a session (password, Google, the second step): whatever the previous person of a
     * shared browser started with Google ends here. The intent to link goes, and so does a request still waiting for
     * Google, so an abandoned return can neither link their Google account nor replace the new sign-in.
     */
    public static void forgetOnSignIn(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return;
        session.removeAttribute(ATTRIBUTE);
        session.removeAttribute(WAITING_AUTHORIZATION_REQUEST);
    }

    /** The waiting intent, if it has not expired; it stays in the session. */
    static GoogleLinkIntent peek(HttpServletRequest request, Instant now) {
        var session = request.getSession(false);
        return session == null ? null : live(session.getAttribute(ATTRIBUTE), now);
    }

    /**
     * Removes the intent from the session and returns it, expired or not: it still names the person to sign in again,
     * while only a valid one allows the link.
     */
    static GoogleLinkIntent take(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return null;
        var stored = session.getAttribute(ATTRIBUTE);
        session.removeAttribute(ATTRIBUTE);
        return stored instanceof GoogleLinkIntent intent ? intent : null;
    }

    boolean validAt(Instant now) {
        return now.isBefore(expiresAt);
    }

    private static GoogleLinkIntent live(Object stored, Instant now) {
        return stored instanceof GoogleLinkIntent intent && intent.validAt(now) ? intent : null;
    }
}
