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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Records the day a person last used the app (UTC, to the day), which decides when an unused account is removed.
 * <p>
 * The update only moves the day forward ({@link UserRepository#markActive}), so it is safe to repeat and to run in
 * parallel. This instance remembers whom it already recorded today and skips them until the next day, so the database
 * sees about one update per person per day and instance. Nothing is kept in the session: parallel first requests of
 * a new session would all try to add the same session attribute, and every one but the first failed on the unique key
 * of the stored attributes (HF-W-012).
 */
public class ActivityFilter extends OncePerRequestFilter {
    /** More people than this on one day and the memory starts over; they are then only recorded once more. */
    static final int MAX_REMEMBERED = 100_000;

    private record Recorded(Instant day, Set<UUID> users) {
    }

    private final UserRepository users;
    private final Clock clock;
    private final AtomicReference<Recorded> recorded = new AtomicReference<>(new Recorded(Instant.EPOCH, ConcurrentHashMap.newKeySet()));

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
            var today = today(day);
            if (!today.users().contains(principal.getUserId())) {
                users.markActive(principal.getUserId(), day);
                // Remembered only after the update went through; a failed one is tried again on the next request.
                if (today.users().size() >= MAX_REMEMBERED) today.users().clear();
                today.users().add(principal.getUserId());
            }
        }
        chain.doFilter(request, response);
    }

    /** The people recorded on {@code day}; a new day starts with nobody. */
    private Recorded today(Instant day) {
        return recorded.updateAndGet(current -> current.day().equals(day) ? current : new Recorded(day, ConcurrentHashMap.newKeySet()));
    }
}
