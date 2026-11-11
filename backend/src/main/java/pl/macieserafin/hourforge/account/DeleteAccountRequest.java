package pl.macieserafin.hourforge.account;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code currentPassword} for an account with a password; {@code confirmByEmail: true} for one without, which asks for
 * a confirmation link instead.
 */
public record DeleteAccountRequest(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String currentPassword, Boolean confirmByEmail) {
    public boolean byEmail() {
        return Boolean.TRUE.equals(confirmByEmail);
    }

    @Override
    public String toString() {
        return "DeleteAccountRequest[redacted]";
    }
}
