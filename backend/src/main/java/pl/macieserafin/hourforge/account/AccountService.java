package pl.macieserafin.hourforge.account;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.auth.PasswordPolicy;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.security.PasswordChangeAttempts;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.util.UUID;

@Service
public class AccountService {
    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final PasswordEncoder encoder;
    private final PasswordChangeAttempts attempts;

    public AccountService(UserRepository users, UserCredentialRepository credentials, PasswordEncoder encoder,
                          PasswordChangeAttempts attempts) {
        this.users = users;
        this.credentials = credentials;
        this.encoder = encoder;
        this.attempts = attempts;
    }

    @Transactional
    public User updateDisplayName(UUID userId, String displayName) {
        var user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        user.setDisplayName(displayName);
        return users.saveAndFlush(user);
    }

    /** Returns the new session version; every other session of the user stops working. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public int changePassword(UUID userId, PasswordChangeRequest request) {
        attempts.requireNotBlocked(userId);
        PasswordPolicy.require(request.newPassword());
        var current = request.currentPassword();
        if (current == null || current.isEmpty() || current.length() > 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required");
        }
        var credential = lockedCredential(userId);
        if (!encoder.matches(current, credential.getPasswordHash())) {
            attempts.recordFailure(userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (request.newPassword().equals(current)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must differ from the current one");
        }
        credential.setPasswordHash(encoder.encode(request.newPassword()));
        int version = credential.invalidateSessions();
        credentials.flush();
        attempts.reset(userId);
        return version;
    }

    @Transactional
    public void signOutEverywhere(UUID userId) {
        lockedCredential(userId).invalidateSessions();
        credentials.flush();
    }

    private UserCredential lockedCredential(UUID userId) {
        return credentials.findLockedByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
