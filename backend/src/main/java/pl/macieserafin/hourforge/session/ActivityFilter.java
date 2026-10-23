package pl.macieserafin.hourforge.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.user.UserRepository;

import java.io.IOException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Records the day a person last used the app (UTC, to the day), which decides when an unused account is removed.
 * The session remembers the day already recorded, so the database is written at most once a day per session.
 */
public class ActivityFilter extends OncePerRequestFilter {
    static final String RECORDED_DAY = ActivityFilter.class.getName() + ".DAY";

    private final UserRepository users;
    private final Clock clock;

    public ActivityFilter(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            var day = clock.instant().truncatedTo(ChronoUnit.DAYS);
            var session = request.getSession(false);
            var today = day.atZone(ZoneOffset.UTC).toLocalDate().toString();
            if (session == null || !today.equals(session.getAttribute(RECORDED_DAY))) {
                users.markActive(principal.getUserId(), day);
                if (session != null) session.setAttribute(RECORDED_DAY, today);
            }
        }
        chain.doFilter(request, response);
    }
}
