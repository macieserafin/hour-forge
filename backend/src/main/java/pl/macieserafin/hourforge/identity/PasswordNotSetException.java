package pl.macieserafin.hourforge.identity;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * An operation that is confirmed with the current password, asked for by an account that has none (it signs in
 * through a linked provider). Answered with 400 and {@code {"code":"PASSWORD_NOT_SET"}}; the password can be set
 * through the password reset.
 */
public class PasswordNotSetException extends ResponseStatusException {
    public static final String CODE = "PASSWORD_NOT_SET";

    public PasswordNotSetException() {
        super(HttpStatus.BAD_REQUEST, "The account has no password");
    }
}
