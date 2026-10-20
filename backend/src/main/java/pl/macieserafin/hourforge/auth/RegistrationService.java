package pl.macieserafin.hourforge.auth;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final PasswordEncoder encoder;
    private final EmailVerificationService verification;
    private final AccountMailService accountMail;

    public RegistrationService(UserRepository users, UserCredentialRepository credentials, PasswordEncoder encoder,
                               EmailVerificationService verification, AccountMailService accountMail) {
        this.users = users;
        this.credentials = credentials;
        this.encoder = encoder;
        this.verification = verification;
        this.accountMail = accountMail;
    }

    @Transactional
    public void register(RegistrationRequest request) {
        var password = request.password();
        PasswordPolicy.require(password);
        // Hashed before the lookup, so a taken address costs the same time as a free one.
        String hash = encoder.encode(password);
        var existing = users.findByEmailIgnoreCase(request.email());
        if (existing.isPresent()) {
            // With e-mail the caller gets the usual answer and the owner of the address is told; without it
            // there is no way to tell the owner, so the caller is told plainly.
            if (!accountMail.enabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
            accountMail.sendRegistrationAttempt(existing.get());
            return;
        }
        User user;
        try {
            user = users.saveAndFlush(new User(request.email(), request.displayName()));
        } catch (DataIntegrityViolationException exception) {
            // The unique index also handles two concurrent registrations of the same address.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uq_users_email_lower".equals(violation.getConstraintName())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
                }
            }
            throw exception;
        }
        credentials.saveAndFlush(new UserCredential(user, hash));
        verification.sendConfirmation(user);
    }
}
