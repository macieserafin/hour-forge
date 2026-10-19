package pl.macieserafin.hourforge.auth;

import java.nio.charset.StandardCharsets;
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

    public RegistrationService(UserRepository users, UserCredentialRepository credentials, PasswordEncoder encoder) {
        this.users = users;
        this.credentials = credentials;
        this.encoder = encoder;
    }

    @Transactional
    public void register(RegistrationRequest request) {
        // Validate here so rejected password values cannot appear in Bean Validation error logs.
        var password = request.password();
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 12 characters and at most 72 UTF-8 bytes");
        }
        if (users.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        String hash = encoder.encode(password);
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
    }
}
