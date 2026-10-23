package pl.macieserafin.hourforge.session;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.IndexResolver;
import org.springframework.session.Session;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sessions are stored in the database (Spring Session JDBC). They are indexed by the user id rather than the e-mail,
 * which can change, so the sessions of one person can be listed and ended.
 */
@Configuration(proxyBeanMethods = false)
public class SessionConfig {
    public static final String COOKIE_NAME = "SESSION";
    /** Set on the sign-in request when "Zapamiętaj mnie" was ticked: the cookie then outlives the browser. */
    public static final String REMEMBER_ATTRIBUTE = SessionConfig.class.getName() + ".REMEMBER";
    public static final Duration REMEMBER_FOR = Duration.ofDays(30);

    @Bean
    CookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName(COOKIE_NAME);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secureCookie);
        serializer.setSameSite("Lax");
        return new RememberingCookieSerializer(serializer);
    }

    @Bean
    SessionRepositoryCustomizer<JdbcIndexedSessionRepository> indexSessionsByUser() {
        return repository -> repository.setIndexResolver(new UserIdIndexResolver());
    }

    static final class UserIdIndexResolver implements IndexResolver<Session> {
        @Override
        public Map<String, String> resolveIndexesFor(Session session) {
            SecurityContext context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (context != null && context.getAuthentication() != null
                    && context.getAuthentication().getPrincipal() instanceof AppUserPrincipal principal) {
                return Map.of(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal.getUserId().toString());
            }
            return Map.of();
        }
    }

    /** A persistent cookie for remembered sign-ins, a browser-session cookie otherwise. */
    static final class RememberingCookieSerializer implements CookieSerializer {
        private final DefaultCookieSerializer delegate;

        RememberingCookieSerializer(DefaultCookieSerializer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void writeCookieValue(CookieValue cookieValue) {
            // An empty value expires the cookie (sign-out) and must keep its max age of 0.
            if (Boolean.TRUE.equals(cookieValue.getRequest().getAttribute(REMEMBER_ATTRIBUTE)) && !cookieValue.getCookieValue().isEmpty()) {
                cookieValue.setCookieMaxAge((int) REMEMBER_FOR.toSeconds());
            }
            delegate.writeCookieValue(cookieValue);
        }

        @Override
        public List<String> readCookieValues(HttpServletRequest request) {
            return delegate.readCookieValues(request);
        }
    }
}
