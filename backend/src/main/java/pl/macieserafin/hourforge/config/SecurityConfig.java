package pl.macieserafin.hourforge.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.google.GoogleSignIn;
import pl.macieserafin.hourforge.logging.SecurityEvents;
import pl.macieserafin.hourforge.notification.NotificationController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.security.LoginAttemptService;
import pl.macieserafin.hourforge.security.SessionVersionFilter;
import pl.macieserafin.hourforge.security.RateLimitProperties;
import pl.macieserafin.hourforge.security.TooManyRequestsException;
import pl.macieserafin.hourforge.session.ActivityFilter;
import pl.macieserafin.hourforge.session.SignInCompletion;
import pl.macieserafin.hourforge.twofactor.TwoFactorSignIn;
import pl.macieserafin.hourforge.session.SessionConfig;
import pl.macieserafin.hourforge.user.UserRepository;

import java.io.IOException;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class SecurityConfig {
    private static final String LOGIN_URL = "/api/auth/login";
    private static final String EMAIL_PARAMETER = "email";
    private static final String REMEMBER_PARAMETER = "rememberMe";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptService loginAttempts,
                                            UserCredentialRepository credentials, SignInCompletion signIns,
                                            TwoFactorSignIn twoFactor, UserRepository users, Clock clock,
                                            ObjectProvider<GoogleSignIn> googleSignIn) throws Exception {
        // Without GOOGLE_CLIENT_ID there is no GoogleSignIn bean: no OAuth2 filter is added and both addresses answer 404.
        var google = googleSignIn.getIfAvailable();
        if (google != null) {
            http.addFilterBefore(google.guard(), OAuth2AuthorizationRequestRedirectFilter.class)
                    .oauth2Login(login -> google.configure(login.loginPage(LOGIN_URL)));
        }
        // Spring's default token store, shared with the sign-in strategy below.
        var csrfTokens = new HttpSessionCsrfTokenRepository();
        return http
                .headers(headers -> headers
                        .referrerPolicy(policy -> policy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        // Same policy as the frontend's: the API never needs these browser features either.
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=()")))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens).sessionAuthenticationStrategy(new StoreNewCsrfTokenAtSignIn(csrfTokens))
                        // Posted by mail providers (RFC 8058), never by a browser session; the random token in the address is the permission.
                        .ignoringRequestMatchers(NotificationController.ONE_CLICK_PATH))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/config").permitAll()
                        // Served only on the management port, which is never published or routed by the proxy; on the
                        // API port these addresses do not exist (404).
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus").permitAll()
                        // Sign-in with Google (start and return); handled by OAuth2 filters before this point when it is on.
                        .requestMatchers(HttpMethod.GET, GoogleSignIn.START_PATH, GoogleSignIn.CALLBACK_PATH).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/email-verification/confirm",
                                "/api/auth/email-verification/resend", "/api/auth/password-reset/request",
                                "/api/auth/password-reset/confirm", "/api/auth/email-change/confirm", "/api/notifications/unsubscribe", NotificationController.ONE_CLICK_PATH, "/api/auth/two-factor",
                                "/api/account/delete/confirm").permitAll()
                        .anyRequest().authenticated())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
                // After CSRF validation, before credentials are checked: a blocked login never reaches BCrypt.
                .addFilterBefore(new LoginRateLimitFilter(loginAttempts), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new SessionVersionFilter(credentials), AuthorizationFilter.class)
                .addFilterAfter(new ActivityFilter(users, clock), SessionVersionFilter.class)
                .formLogin(form -> form
                        .loginPage(LOGIN_URL)
                        .loginProcessingUrl(LOGIN_URL)
                        .usernameParameter(EMAIL_PARAMETER)
                        .successHandler((request, response, authentication) -> {
                            if (authentication.getPrincipal() instanceof AppUserPrincipal principal && !principal.isEmailVerified()) {
                                // The password was right, so this reveals nothing to a stranger; the session is dropped again.
                                new SecurityContextLogoutHandler().logout(request, response, authentication);
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"code\":\"EMAIL_NOT_VERIFIED\"}");
                                return;
                            }
                            if (authentication.getPrincipal() instanceof AppUserPrincipal principal && principal.isBlocked()) {
                                // Told only after a right password, like the unconfirmed address above.
                                new SecurityContextLogoutHandler().logout(request, response, authentication);
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"code\":\"ACCOUNT_BLOCKED\"}");
                                return;
                            }
                            loginAttempts.recordSuccess(request.getParameter(EMAIL_PARAMETER), request.getRemoteAddr());
                            var remembered = "true".equals(request.getParameter(REMEMBER_PARAMETER));
                            var signedIn = (AppUserPrincipal) authentication.getPrincipal();
                            var userId = signedIn.getUserId();
                            if (twoFactor.required(userId)) {
                                // Not signed in yet: the session waits for the code (POST /api/auth/two-factor).
                                twoFactor.begin(request, userId, remembered, signedIn.getSessionVersion());
                                response.setStatus(HttpServletResponse.SC_ACCEPTED);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"code\":\"TWO_FACTOR_REQUIRED\"}");
                                return;
                            }
                            signIns.complete(request, userId, remembered);
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        })
                        .failureHandler((request, response, exception) -> {
                            loginAttempts.recordFailure(request.getParameter(EMAIL_PARAMETER), request.getRemoteAddr());
                            signIns.failPassword(request, request.getParameter(EMAIL_PARAMETER));
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        })
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies(SessionConfig.COOKIE_NAME)
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
                                SecurityEvents.record("sign-out", principal.getUserId());
                            }
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        }))
                .build();
    }

    /**
     * A sign-in replaces the CSRF token, as Spring's {@code CsrfAuthenticationStrategy} does, but stores the new one at
     * once instead of at its first use. Otherwise the first requests after a sign-in that need the token (the frontend
     * asks for it before every change) each create one and add the same session attribute; with sessions stored in the
     * database all but one then fail on the attribute's unique key (500). Applies to every sign-in that goes through
     * Spring's authentication filters: the password and Google; the second step keeps the token of the first.
     */
    private static final class StoreNewCsrfTokenAtSignIn implements SessionAuthenticationStrategy {
        private final CsrfTokenRepository tokens;
        private final CsrfTokenRequestHandler requestHandler = new XorCsrfTokenRequestAttributeHandler();

        private StoreNewCsrfTokenAtSignIn(CsrfTokenRepository tokens) {
            this.tokens = tokens;
        }

        @Override
        public void onAuthentication(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
            tokens.saveToken(null, request, response);
            var token = tokens.generateToken(request);
            tokens.saveToken(token, request, response);
            requestHandler.handle(request, response, () -> token);
        }
    }

    // Not a bean: a Filter bean would also be registered in the servlet container outside the security chain.
    private static final class LoginRateLimitFilter extends OncePerRequestFilter {
        private final LoginAttemptService loginAttempts;

        private LoginRateLimitFilter(LoginAttemptService loginAttempts) {
            this.loginAttempts = loginAttempts;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !(HttpMethod.POST.matches(request.getMethod()) && LOGIN_URL.equals(request.getRequestURI().substring(request.getContextPath().length())));
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            var blocked = loginAttempts.blockedFor(request.getParameter(EMAIL_PARAMETER), request.getRemoteAddr());
            if (blocked.isPresent()) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(TooManyRequestsException.retryAfterSeconds(blocked.get())));
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
