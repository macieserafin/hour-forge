package pl.macieserafin.hourforge.google;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.security.DatabaseUserDetailsService;
import pl.macieserafin.hourforge.session.LoginEvent;
import pl.macieserafin.hourforge.session.SignInCompletion;
import pl.macieserafin.hourforge.twofactor.TwoFactorSignIn;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finishes the return from Google with a redirect to the frontend, never with a page or JSON of its own. After Spring
 * has checked the state, the PKCE verifier and the ID token (signature, issuer, audience, expiry, nonce), the same checks
 * as for a password follow, in the same order: removed, blocked, confirmed address, second step (D3), then the sign-in.
 * The session then holds the application's own principal with the credential version, never Google's tokens or claims.
 */
final class GoogleSignInHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(GoogleSignInHandler.class);
    private static final Pattern ERROR_CODE = Pattern.compile("[a-z_]{1,64}");

    private final GoogleAccounts accounts;
    private final DatabaseUserDetailsService userDetails;
    private final TwoFactorSignIn twoFactor;
    private final SignInCompletion completion;
    private final String frontendUrl;

    GoogleSignInHandler(GoogleAccounts accounts, DatabaseUserDetailsService userDetails, TwoFactorSignIn twoFactor,
                        SignInCompletion completion, String frontendUrl) {
        this.accounts = accounts;
        this.userDetails = userDetails;
        this.twoFactor = twoFactor;
        this.completion = completion;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        var intent = GoogleIntent.current(request);
        // Spring has just stored its own authentication (with the ID token) in the session: it never stays there.
        forgetAuthentication(request);
        if (!(authentication.getPrincipal() instanceof OidcUser google)) {
            redirect(response, refusal(intent, GoogleOutcome.GOOGLE_FAILED));
            return;
        }
        try {
            switch (intent.mode()) {
                case LOGIN -> signIn(request, response, intent, GoogleClaims.of(google));
                // A13 K3 links Google to the signed-in account here; until then the start refuses this mode.
                case LINK -> redirect(response, refusal(intent, GoogleOutcome.GOOGLE_FAILED));
            }
        } catch (DataIntegrityViolationException exception) {
            forgetAuthentication(request);
            redirect(response, refusal(intent, GoogleAccounts.raceOutcome(exception)));
        } catch (RuntimeException exception) {
            forgetAuthentication(request);
            // Only the kind of failure: messages of the token and account code may carry claims.
            log.warn("Sign-in with Google could not be finished: {}", exception.getClass().getName());
            redirect(response, refusal(intent, GoogleOutcome.GOOGLE_FAILED));
        }
    }

    private void signIn(HttpServletRequest request, HttpServletResponse response, GoogleIntent intent, GoogleClaims claims)
            throws IOException {
        var account = accounts.signIn(claims, request.getRemoteAddr());
        if (account.refusal() != null) {
            redirect(response, refusal(intent, account.refusal()));
            return;
        }
        AppUserPrincipal principal;
        try {
            principal = (AppUserPrincipal) userDetails.loadUserByUsername(account.email());
        } catch (UsernameNotFoundException exception) {
            redirect(response, refusal(intent, GoogleOutcome.GOOGLE_FAILED));
            return;
        }
        if (!account.userId().equals(principal.getUserId())) {
            redirect(response, refusal(intent, GoogleOutcome.GOOGLE_FAILED));
            return;
        }
        if (principal.isBlocked()) {
            redirect(response, refusal(intent, GoogleOutcome.ACCOUNT_BLOCKED));
            return;
        }
        if (!principal.isEmailVerified()) {
            redirect(response, refusal(intent, GoogleOutcome.EMAIL_NOT_VERIFIED));
            return;
        }
        if (twoFactor.required(principal.getUserId())) {
            // Not signed in yet: the frontend asks for the code and posts it to /api/auth/two-factor as after a password.
            twoFactor.begin(request, principal.getUserId(), intent.remembered(), principal.getSessionVersion(), LoginEvent.Method.GOOGLE);
            redirect(response, "/login?step=two-factor" + returnToParameter(intent));
            return;
        }
        principal.eraseCredentials();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        completion.complete(request, principal.getUserId(), intent.remembered(), LoginEvent.Method.GOOGLE);
        redirect(response, intent.returnTo());
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        var intent = GoogleIntent.current(request);
        forgetAuthentication(request);
        var error = exception instanceof OAuth2AuthenticationException oauth ? oauth.getError().getErrorCode() : null;
        var cancelled = OAuth2ErrorCodes.ACCESS_DENIED.equals(error);
        // Only the error code: descriptions, the code, the state and the tokens stay out of the log.
        log.info("Sign-in with Google did not finish: {}", error != null && ERROR_CODE.matcher(error).matches() ? error : "other");
        redirect(response, refusal(intent, cancelled ? GoogleOutcome.GOOGLE_CANCELLED : GoogleOutcome.GOOGLE_FAILED));
    }

    /** Where a sign-in that did not happen goes; the account page for a link (C9), the sign-in page otherwise. */
    static String refusal(GoogleIntent intent, GoogleOutcome outcome) {
        if (intent.mode() == GoogleIntent.Mode.LINK) return "/account?google=" + outcome.name() + "#google";
        return "/login?google=" + outcome.name() + returnToParameter(intent);
    }

    private static String returnToParameter(GoogleIntent intent) {
        return GoogleIntent.DEFAULT_RETURN_TO.equals(intent.returnTo()) ? ""
                : "&returnTo=" + URLEncoder.encode(intent.returnTo(), StandardCharsets.UTF_8);
    }

    private static void forgetAuthentication(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        request.removeAttribute(RequestAttributeSecurityContextRepository.DEFAULT_REQUEST_ATTR_NAME);
        var session = request.getSession(false);
        if (session != null) session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    }

    private void redirect(HttpServletResponse response, String path) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.sendRedirect(frontendUrl + path);
    }
}
