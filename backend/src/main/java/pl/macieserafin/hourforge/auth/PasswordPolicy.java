package pl.macieserafin.hourforge.auth;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PasswordPolicy {
    private PasswordPolicy() {
    }

    /** Checked in code, not with Bean Validation, so rejected password values cannot appear in validation error logs. */
    public static void require(String password) {
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 12 characters and at most 72 UTF-8 bytes");
        }
    }
}
