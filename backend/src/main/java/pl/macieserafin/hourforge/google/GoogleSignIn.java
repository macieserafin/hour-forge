package pl.macieserafin.hourforge.google;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.security.DatabaseUserDetailsService;
import pl.macieserafin.hourforge.security.RequestRateLimits;
import pl.macieserafin.hourforge.security.TooManyRequestsException;
import pl.macieserafin.hourforge.session.SignInCompletion;
import pl.macieserafin.hourforge.twofactor.TwoFactorSignIn;
import pl.macieserafin.hourforge.user.UserRepository;

import java.io.IOException;
import java.time.Clock;

/**
 * Everything {@code SecurityConfig} adds to its one filter chain when the sign-in with Google is on. The browser starts
 * at {@value #AUTHORIZATION_BASE}/google and comes back to {@value #CALLBACK_PATH}; both are under {@code /api}, so the
 * Vite proxy and a reverse proxy pass them like the rest of the API.
 */
@Component
@Conditional(GoogleSignInEnabled.class)
public class GoogleSignIn {
    public static final String REGISTRATION_ID = "google";
    public static final String AUTHORIZATION_BASE = "/api/auth/oauth2/authorization";
    public static final String CALLBACK_BASE = "/api/auth/oauth2/callback";
    public static final String START_PATH = AUTHORIZATION_BASE + "/" + REGISTRATION_ID;
    public static final String CALLBACK_PATH = CALLBACK_BASE + "/" + REGISTRATION_ID;

    /** Keeps nothing: Google's access token is not needed after the sign-in, so it is neither stored nor kept in memory. */
    private static final OAuth2AuthorizedClientRepository KEEP_NO_CLIENT = new OAuth2AuthorizedClientRepository() {
        @Override
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId, Authentication principal,
                                                                         HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
                                         HttpServletRequest request, HttpServletResponse response) {
        }

        @Override
        public void removeAuthorizedClient(String clientRegistrationId, Authentication principal, HttpServletRequest request,
                                           HttpServletResponse response) {
        }
    };

    private final ClientRegistrationRepository registrations;
    private final RequestRateLimits rateLimits;
    private final GoogleSignInHandler handler;
    private final String frontendUrl;
    private final Clock clock;

    GoogleSignIn(ClientRegistrationRepository registrations, RequestRateLimits rateLimits, GoogleAccounts accounts, GoogleLinks links,
                 DatabaseUserDetailsService userDetails, UserRepository users, TwoFactorSignIn twoFactor, SignInCompletion completion,
                 Clock clock, @Value("${app.frontend-url}") String frontendUrl) {
        this.registrations = registrations;
        this.rateLimits = rateLimits;
        this.clock = clock;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.handler = new GoogleSignInHandler(accounts, links, userDetails, users, twoFactor, completion, clock, this.frontendUrl);
    }

    /** The OAuth2 login of the chain: our addresses, PKCE and nonce, no kept tokens, redirects to the frontend only. */
    public void configure(OAuth2LoginConfigurer<HttpSecurity> login) {
        login.clientRegistrationRepository(registrations)
                .authorizedClientRepository(KEEP_NO_CLIENT)
                .authorizationEndpoint(endpoint -> endpoint
                        .baseUri(AUTHORIZATION_BASE)
                        .authorizationRequestRepository(new GoogleAuthorizationRequests())
                        .authorizationRequestResolver(GoogleAuthorizationRequests.resolver(registrations)))
                // Exactly the one callback address: Spring takes the registration from the stored request, not the path.
                .redirectionEndpoint(endpoint -> endpoint.baseUri(CALLBACK_PATH))
                .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(GoogleSignInConfig.tokenClient()))
                .successHandler(handler)
                .failureHandler(handler);
    }

    /**
     * Goes before Spring's start filter and covers every address under both prefixes: the per-address limit, the
     * allowed modes, the link intent at the start, and a plain refusal for any other registration or path.
     */
    public Filter guard() {
        return new Guard();
    }

    // Not a bean: a Filter bean would also be registered in the servlet container outside the security chain.
    private final class Guard extends OncePerRequestFilter {
        private final HttpSessionOAuth2AuthorizationRequestRepository stored = new HttpSessionOAuth2AuthorizationRequestRepository();

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            var path = path(request);
            return !(path.startsWith(AUTHORIZATION_BASE + "/") || path.startsWith(CALLBACK_BASE + "/"));
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            var path = path(request);
            var start = path.startsWith(AUTHORIZATION_BASE + "/");
            var intent = start ? GoogleIntent.fromStart(request) : returningIntent(request);
            try {
                rateLimits.googleSignIn(request.getRemoteAddr());
            } catch (TooManyRequestsException exception) {
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()));
                redirect(response, GoogleSignInHandler.refusal(intent, GoogleOutcome.GOOGLE_FAILED));
                return;
            }
            // Only Google, only by GET, only at the two addresses; anything else is refused instead of reaching Spring.
            if (!HttpMethod.GET.matches(request.getMethod()) || !(START_PATH.equals(path) || CALLBACK_PATH.equals(path))) {
                redirect(response, GoogleSignInHandler.refusal(intent, GoogleOutcome.GOOGLE_FAILED));
                return;
            }
            if (start) {
                var mode = GoogleIntent.Mode.parse(request.getParameter("mode"));
                if (mode == null) {
                    redirect(response, GoogleSignInHandler.refusal(intent, GoogleOutcome.GOOGLE_FAILED));
                    return;
                }
                // Linking leaves for Google only from the session that was given the intent, while it is valid.
                if (mode == GoogleIntent.Mode.LINK && !linkAllowed(request)) {
                    redirect(response, GoogleSignInHandler.refusal(intent, GoogleOutcome.LINK_EXPIRED));
                    return;
                }
            }
            chain.doFilter(request, response);
        }

        /** The intent of the waiting authorization request this callback answers (matched by its state), if any. */
        private GoogleIntent returningIntent(HttpServletRequest request) {
            var waiting = request.getParameter("state") == null ? null : stored.loadAuthorizationRequest(request);
            return waiting == null ? new GoogleIntent(GoogleIntent.Mode.LOGIN, false, GoogleIntent.DEFAULT_RETURN_TO) : GoogleIntent.from(waiting);
        }

        private boolean linkAllowed(HttpServletRequest request) {
            var link = GoogleLinkIntent.peek(request, clock.instant());
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            return link != null && authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
                    && link.userId().equals(principal.getUserId());
        }

        private void redirect(HttpServletResponse response, String path) throws IOException {
            response.setHeader("Cache-Control", "no-store");
            response.sendRedirect(frontendUrl + path);
        }
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
