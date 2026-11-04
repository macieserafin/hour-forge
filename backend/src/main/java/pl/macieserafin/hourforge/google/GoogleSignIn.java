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
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.macieserafin.hourforge.security.DatabaseUserDetailsService;
import pl.macieserafin.hourforge.security.RequestRateLimits;
import pl.macieserafin.hourforge.security.TooManyRequestsException;
import pl.macieserafin.hourforge.session.SignInCompletion;
import pl.macieserafin.hourforge.twofactor.TwoFactorSignIn;

import java.io.IOException;

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

    GoogleSignIn(ClientRegistrationRepository registrations, RequestRateLimits rateLimits, GoogleAccounts accounts,
                 DatabaseUserDetailsService userDetails, TwoFactorSignIn twoFactor, SignInCompletion completion,
                 @Value("${app.frontend-url}") String frontendUrl) {
        this.registrations = registrations;
        this.rateLimits = rateLimits;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.handler = new GoogleSignInHandler(accounts, userDetails, twoFactor, completion, this.frontendUrl);
    }

    /** The OAuth2 login of the chain: our addresses, PKCE and nonce, no kept tokens, redirects to the frontend only. */
    public void configure(OAuth2LoginConfigurer<HttpSecurity> login) {
        login.clientRegistrationRepository(registrations)
                .authorizedClientRepository(KEEP_NO_CLIENT)
                .authorizationEndpoint(endpoint -> endpoint
                        .baseUri(AUTHORIZATION_BASE)
                        .authorizationRequestRepository(new GoogleAuthorizationRequests())
                        .authorizationRequestResolver(GoogleAuthorizationRequests.resolver(registrations)))
                .redirectionEndpoint(endpoint -> endpoint.baseUri(CALLBACK_BASE + "/*"))
                .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(GoogleSignInConfig.tokenClient()))
                .successHandler(handler)
                .failureHandler(handler);
    }

    /** Goes before Spring's start filter: the per-address limit for start and callback, and the allowed modes. */
    public Filter guard() {
        return new Guard();
    }

    // Not a bean: a Filter bean would also be registered in the servlet container outside the security chain.
    private final class Guard extends OncePerRequestFilter {
        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            var path = request.getRequestURI().substring(request.getContextPath().length());
            return !(HttpMethod.GET.matches(request.getMethod()) && (START_PATH.equals(path) || CALLBACK_PATH.equals(path)));
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            var path = request.getRequestURI().substring(request.getContextPath().length());
            var start = START_PATH.equals(path);
            var intent = start ? GoogleIntent.fromStart(request) : new GoogleIntent(GoogleIntent.Mode.LOGIN, false, GoogleIntent.DEFAULT_RETURN_TO);
            if (start) {
                var mode = GoogleIntent.Mode.parse(request.getParameter("mode"));
                // Linking (mode=link) arrives with A13 K3; until then it does not leave for Google at all.
                if (mode != GoogleIntent.Mode.LOGIN) {
                    var refused = new GoogleIntent(mode == null ? GoogleIntent.Mode.LOGIN : mode, false, intent.returnTo());
                    redirect(response, GoogleSignInHandler.refusal(refused, GoogleOutcome.GOOGLE_FAILED));
                    return;
                }
            }
            try {
                rateLimits.googleSignIn(request.getRemoteAddr());
            } catch (TooManyRequestsException exception) {
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()));
                redirect(response, GoogleSignInHandler.refusal(intent, GoogleOutcome.GOOGLE_FAILED));
                return;
            }
            chain.doFilter(request, response);
        }

        private void redirect(HttpServletResponse response, String path) throws IOException {
            response.setHeader("Cache-Control", "no-store");
            response.sendRedirect(frontendUrl + path);
        }
    }
}
