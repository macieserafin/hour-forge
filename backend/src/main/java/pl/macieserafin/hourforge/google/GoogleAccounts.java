package pl.macieserafin.hourforge.google;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.auth.AccountMailService;
import pl.macieserafin.hourforge.auth.EmailVerificationService;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.identity.IdentityProvider;
import pl.macieserafin.hourforge.identity.UserIdentity;
import pl.macieserafin.hourforge.identity.UserIdentityRepository;
import pl.macieserafin.hourforge.security.RequestRateLimits;
import pl.macieserafin.hourforge.security.TooManyRequestsException;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Clock;
import java.util.UUID;

/**
 * Finds or creates the account behind a Google sign-in. An account is found only by the Google {@code sub}; an address
 * that already has an account never links or creates anything (A13.2, D1).
 */
@Service
class GoogleAccounts {
    /** The account to sign in to, or why there is none. */
    record Result(UUID userId, String email, GoogleOutcome refusal) {
        static Result signIn(User user) {
            return new Result(user.getId(), user.getEmail(), null);
        }

        static Result refuse(GoogleOutcome outcome) {
            return new Result(null, null, outcome);
        }
    }

    private final UserIdentityRepository identities;
    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final EmailVerificationService verification;
    private final AccountMailService mail;
    private final RequestRateLimits rateLimits;
    private final Clock clock;

    GoogleAccounts(UserIdentityRepository identities, UserRepository users, UserCredentialRepository credentials,
                   EmailVerificationService verification, AccountMailService mail, RequestRateLimits rateLimits, Clock clock) {
        this.identities = identities;
        this.users = users;
        this.credentials = credentials;
        this.verification = verification;
        this.mail = mail;
        this.rateLimits = rateLimits;
        this.clock = clock;
    }

    @Transactional
    public Result signIn(GoogleClaims claims, String ip) {
        if (!claims.usableSubject()) return Result.refuse(GoogleOutcome.GOOGLE_FAILED);
        var now = clock.instant();
        var linked = identities.findByProviderAndSubject(IdentityProvider.GOOGLE, claims.subject());
        if (linked.isPresent()) {
            var user = linked.get().getUser();
            // Removing an account deletes its link; this only guards against a link left behind.
            if (user.getDeletedAt() != null) return Result.refuse(GoogleOutcome.GOOGLE_FAILED);
            // The local address stays as it is (D13); the Google address shown in the settings follows Google.
            linked.get().recordUse(now, claims.emailForDisplay());
            return Result.signIn(user);
        }
        // No account without an address Google has verified (D4).
        if (!claims.usableEmail()) return Result.refuse(GoogleOutcome.GOOGLE_EMAIL_UNVERIFIED);
        var existing = users.findByEmailIgnoreCase(claims.email());
        if (existing.isPresent()) {
            // Whoever proved this Google account learns that the address has an account; its owner is told (D1).
            mail.sendGoogleSignInAttempt(existing.get());
            return Result.refuse(GoogleOutcome.GOOGLE_ACCOUNT_EXISTS);
        }
        try {
            // Shares the limit of the password registration; no bot check, Google already asks a person (D9).
            rateLimits.registration(ip);
        } catch (TooManyRequestsException exception) {
            return Result.refuse(GoogleOutcome.GOOGLE_FAILED);
        }
        var user = new User(claims.email(), claims.displayName());
        if (claims.authoritative()) user.setEmailVerifiedAt(now);
        user = users.saveAndFlush(user);
        credentials.saveAndFlush(UserCredential.withoutPassword(user));
        var identity = new UserIdentity(user, IdentityProvider.GOOGLE, claims.subject(), claims.emailForDisplay(), now);
        identity.recordUse(now, claims.emailForDisplay());
        identities.saveAndFlush(identity);
        // Not vouched for by Google: confirmed with our own link, like a password registration (D4, F10).
        if (!user.isEmailVerified()) verification.sendConfirmation(user);
        return Result.signIn(user);
    }

    /**
     * What a lost race of two sign-ins means: the address was taken in the meantime, or the same Google account was
     * linked by the other request (signing in again then works).
     */
    static GoogleOutcome raceOutcome(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && "uq_users_email_lower".equals(violation.getConstraintName())) {
                return GoogleOutcome.GOOGLE_ACCOUNT_EXISTS;
            }
        }
        return GoogleOutcome.GOOGLE_FAILED;
    }
}
