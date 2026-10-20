package pl.macieserafin.hourforge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;

import java.io.IOException;

/**
 * Ends sessions issued before a password change or "sign out everywhere". The stored version is compared on
 * every authenticated request, so the check also holds after a restart or when sessions are moved to a database.
 */
public class SessionVersionFilter extends OncePerRequestFilter {
    private final UserCredentialRepository credentials;

    public SessionVersionFilter(UserCredentialRepository credentials) {
        this.credentials = credentials;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
                && principal.getSessionVersion() != null) {
            var current = credentials.findSessionVersion(principal.getUserId());
            // A missing credential (for example a removed account) is treated like an outdated session.
            if (current.isEmpty() || !current.get().equals(principal.getSessionVersion())) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }
}
