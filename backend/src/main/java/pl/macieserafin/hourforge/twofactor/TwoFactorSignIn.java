package pl.macieserafin.hourforge.twofactor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.security.DatabaseUserDetailsService;
import pl.macieserafin.hourforge.session.SignInCompletion;
import pl.macieserafin.hourforge.user.UserRepository;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The second step of a sign-in. A right password for an account with two-step sign-in does not sign in: the session
 * only remembers, for five minutes, who passed the first step. The code then finishes the sign-in on a new session id.
 */
@Component
public class TwoFactorSignIn {
    static final String PENDING = TwoFactorSignIn.class.getName() + ".PENDING";
    /** The credential version the password was checked against; a separate attribute keeps stored sessions readable. */
    static final String PENDING_VERSION = TwoFactorSignIn.class.getName() + ".PENDING_VERSION";
    static final Duration TIME_TO_ANSWER = Duration.ofMinutes(5);
    static final int ATTEMPTS = 5;

    /** Who passed the password step, whether to remember the device, and until when the code is awaited. */
    record Pending(UUID userId, boolean remembered, Instant expiresAt, int failures) implements Serializable {
    }

    public enum Result { SIGNED_IN, WRONG_CODE, START_AGAIN }

    private final TwoFactorService twoFactor;
    private final DatabaseUserDetailsService userDetails;
    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final SignInCompletion completion;
    private final Clock clock;

    public TwoFactorSignIn(TwoFactorService twoFactor, DatabaseUserDetailsService userDetails, UserRepository users,
                           UserCredentialRepository credentials, SignInCompletion completion, Clock clock) {
        this.twoFactor = twoFactor;
        this.userDetails = userDetails;
        this.users = users;
        this.credentials = credentials;
        this.completion = completion;
        this.clock = clock;
    }

    public boolean required(UUID userId) {
        return twoFactor.isEnabled(userId);
    }

    /**
     * Undoes the sign-in the password step made and waits for the code instead. The credential version the password
     * was checked against is kept, so a password change, reset or "sign out everywhere" ends the waiting sign-in.
     */
    public void begin(HttpServletRequest request, UUID userId, boolean remembered, Integer credentialVersion) {
        SecurityContextHolder.clearContext();
        var session = request.getSession();
        session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        session.setAttribute(PENDING_VERSION, credentialVersion);
        session.setAttribute(PENDING, new Pending(userId, remembered, clock.instant().plus(TIME_TO_ANSWER), 0));
    }

    public Result answer(HttpServletRequest request, HttpServletResponse response, String code) {
        var session = request.getSession(false);
        var pending = session == null ? null : (Pending) session.getAttribute(PENDING);
        if (pending == null || clock.instant().isAfter(pending.expiresAt())) {
            if (session != null) forget(session);
            return Result.START_AGAIN;
        }
        var version = (Integer) session.getAttribute(PENDING_VERSION);
        // The password was right for an older state of the account: the first step has to be repeated.
        if (!isCurrent(pending.userId(), version)) {
            forget(session);
            return Result.START_AGAIN;
        }
        if (!twoFactor.verifySignIn(pending.userId(), code)) {
            completion.failSecondStep(request, pending.userId());
            var failures = pending.failures() + 1;
            if (failures >= ATTEMPTS) {
                forget(session);
                return Result.START_AGAIN;
            }
            session.setAttribute(PENDING, new Pending(pending.userId(), pending.remembered(), pending.expiresAt(), failures));
            return Result.WRONG_CODE;
        }
        forget(session);
        // Read again: the account may have changed (new password, removed) while the code was being typed.
        var user = users.findById(pending.userId()).filter(found -> found.getDeletedAt() == null);
        if (user.isEmpty()) return Result.START_AGAIN;
        AppUserPrincipal principal;
        try {
            principal = (AppUserPrincipal) userDetails.loadUserByUsername(user.get().getEmail());
        } catch (UsernameNotFoundException exception) {
            // The address or account can disappear between the two reads.
            return Result.START_AGAIN;
        }
        if (!pending.userId().equals(principal.getUserId()) || principal.isBlocked()
                || !version.equals(principal.getSessionVersion())) return Result.START_AGAIN;
        principal.eraseCredentials();
        request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        completion.complete(request, pending.userId(), pending.remembered());
        return Result.SIGNED_IN;
    }

    private boolean isCurrent(UUID userId, Integer version) {
        return version != null && credentials.findSessionVersion(userId).filter(version::equals).isPresent();
    }

    private static void forget(jakarta.servlet.http.HttpSession session) {
        session.removeAttribute(PENDING);
        session.removeAttribute(PENDING_VERSION);
    }
}
