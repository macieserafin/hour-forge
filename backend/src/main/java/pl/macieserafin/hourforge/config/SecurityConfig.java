package pl.macieserafin.hourforge.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.mail.EmailVerificationProperties;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.security.LoginAttemptService;
import pl.macieserafin.hourforge.security.SessionVersionFilter;
import pl.macieserafin.hourforge.security.RateLimitProperties;
import pl.macieserafin.hourforge.security.TooManyRequestsException;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class SecurityConfig {
    private static final String LOGIN_URL = "/api/auth/login";
    private static final String EMAIL_PARAMETER = "email";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptService loginAttempts,
                                            UserCredentialRepository credentials,
                                            EmailVerificationProperties verification) throws Exception {
        return http
                .headers(headers -> headers.referrerPolicy(policy -> policy.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/email-verification/confirm",
                                "/api/auth/email-verification/resend").permitAll()
                        .anyRequest().authenticated())
                .requestCache(cache -> cache.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
                // After CSRF validation, before credentials are checked: a blocked login never reaches BCrypt.
                .addFilterBefore(new LoginRateLimitFilter(loginAttempts), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new SessionVersionFilter(credentials), AuthorizationFilter.class)
                .formLogin(form -> form
                        .loginPage(LOGIN_URL)
                        .loginProcessingUrl(LOGIN_URL)
                        .usernameParameter(EMAIL_PARAMETER)
                        .successHandler((request, response, authentication) -> {
                            if (verification.required() && authentication.getPrincipal() instanceof AppUserPrincipal principal
                                    && !principal.isEmailVerified()) {
                                // The password was right, so this reveals nothing to a stranger; the session is dropped again.
                                new SecurityContextLogoutHandler().logout(request, response, authentication);
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"code\":\"EMAIL_NOT_VERIFIED\"}");
                                return;
                            }
                            loginAttempts.recordSuccess(request.getParameter(EMAIL_PARAMETER), request.getRemoteAddr());
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        })
                        .failureHandler((request, response, exception) -> {
                            loginAttempts.recordFailure(request.getParameter(EMAIL_PARAMETER), request.getRemoteAddr());
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        })
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .build();
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
