package pl.macieserafin.hourforge.google;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.auth.AccountMailService;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.identity.IdentityProvider;
import pl.macieserafin.hourforge.identity.UserIdentity;
import pl.macieserafin.hourforge.identity.UserIdentityRepository;
import pl.macieserafin.hourforge.logging.SecurityEvents;
import pl.macieserafin.hourforge.security.PasswordChangeAttempts;

import java.time.Clock;
import java.util.UUID;

/**
 * Linking and unlinking Google on the account page. Both are confirmed with the current password when the
 * account has one, and wrong answers share the limit of the password change. One Google account per account.
 */
@Service
public class GoogleLinks {
    private final UserCredentialRepository credentials;
    private final UserIdentityRepository identities;
    private final PasswordEncoder encoder;
    private final PasswordChangeAttempts attempts;
    private final AccountMailService mail;
    private final Clock clock;

    GoogleLinks(UserCredentialRepository credentials, UserIdentityRepository identities, PasswordEncoder encoder,
                PasswordChangeAttempts attempts, AccountMailService mail, Clock clock) {
        this.credentials = credentials;
        this.identities = identities;
        this.encoder = encoder;
        this.attempts = attempts;
        this.mail = mail;
        this.clock = clock;
    }

    /** Checks that linking may start and returns the credential version the intent is bound to. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public int prepareLink(UUID userId, String currentPassword) {
        attempts.requireNotBlocked(userId);
        var credential = lockedCredential(userId);
        if (credential.hasPassword()) requirePassword(credential, currentPassword);
        if (identities.findByUserIdAndProvider(userId, IdentityProvider.GOOGLE).isPresent()) {
            throw new GoogleLinkException(HttpStatus.CONFLICT, GoogleLinkException.ALREADY_LINKED_TO_YOU);
        }
        attempts.reset(userId);
        return credential.getSessionVersion();
    }

    /**
     * Links the Google account to the person of a valid intent. The Google address need not be the account's address;
     * it is kept only for display.
     */
    @Transactional
    public GoogleOutcome link(GoogleLinkIntent intent, GoogleClaims claims) {
        if (!claims.usableSubject()) return GoogleOutcome.GOOGLE_FAILED;
        var credential = credentials.findLockedByUserId(intent.userId());
        if (credential.isEmpty() || credential.get().getSessionVersion() != intent.sessionVersion()
                || credential.get().getUser().getDeletedAt() != null) {
            return GoogleOutcome.LINK_EXPIRED;
        }
        var user = credential.get().getUser();
        var owner = identities.findByProviderAndSubject(IdentityProvider.GOOGLE, claims.subject());
        if (owner.isPresent()) {
            return owner.get().getUser().getId().equals(user.getId()) ? GoogleOutcome.GOOGLE_ALREADY_LINKED_TO_YOU
                    : GoogleOutcome.GOOGLE_ALREADY_LINKED;
        }
        if (identities.findByUserIdAndProvider(user.getId(), IdentityProvider.GOOGLE).isPresent()) {
            return GoogleOutcome.GOOGLE_ALREADY_LINKED_TO_YOU;
        }
        identities.saveAndFlush(new UserIdentity(user, IdentityProvider.GOOGLE, claims.subject(), claims.emailForDisplay(), clock.instant()));
        mail.sendGoogleLinked(user, claims.emailForDisplay());
        SecurityEvents.record("google.linked", user.getId());
        return GoogleOutcome.LINKED;
    }

    /**
     * Removes the link and returns the new credential version: every other session ends, also those signed in
     * with Google. The last way to sign in cannot be removed.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public int unlink(UUID userId, String currentPassword) {
        attempts.requireNotBlocked(userId);
        var credential = lockedCredential(userId);
        if (!credential.hasPassword()) throw new GoogleLinkException(HttpStatus.CONFLICT, GoogleLinkException.LAST_SIGN_IN_METHOD);
        requirePassword(credential, currentPassword);
        var identity = identities.findByUserIdAndProvider(userId, IdentityProvider.GOOGLE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No Google account is linked"));
        identities.delete(identity);
        var version = credential.invalidateSessions();
        credentials.flush();
        attempts.reset(userId);
        mail.sendGoogleUnlinked(credential.getUser(), identity.getEmail());
        SecurityEvents.record("google.unlinked", userId);
        return version;
    }

    /** What a lost race of two links means: the Google account or this account was linked by the other request. */
    static GoogleOutcome raceOutcome(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                if ("uq_user_identities_provider_subject".equals(violation.getConstraintName())) return GoogleOutcome.GOOGLE_ALREADY_LINKED;
                if ("uq_user_identities_user_provider".equals(violation.getConstraintName())) return GoogleOutcome.GOOGLE_ALREADY_LINKED_TO_YOU;
            }
        }
        return GoogleOutcome.GOOGLE_FAILED;
    }

    private void requirePassword(UserCredential credential, String currentPassword) {
        if (currentPassword == null || currentPassword.isEmpty() || currentPassword.length() > 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required");
        }
        if (!encoder.matches(currentPassword, credential.getPasswordHash())) {
            attempts.recordFailure(credential.getUserId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
    }

    private UserCredential lockedCredential(UUID userId) {
        return credentials.findLockedByUserId(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
