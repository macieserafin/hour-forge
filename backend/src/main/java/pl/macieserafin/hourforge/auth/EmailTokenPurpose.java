package pl.macieserafin.hourforge.auth;

import java.time.Duration;

public enum EmailTokenPurpose {
    VERIFY_EMAIL(Duration.ofHours(24), false),
    RESET_PASSWORD(Duration.ofHours(1), true),
    CHANGE_EMAIL(Duration.ofHours(24), true),
    /** Confirms the removal of an account without a password; dies with a password, unlink or sign-out-everywhere. */
    DELETE_ACCOUNT(Duration.ofHours(24), true);

    private final Duration validity;
    private final boolean boundToCredentials;

    EmailTokenPurpose(Duration validity, boolean boundToCredentials) {
        this.validity = validity;
        this.boundToCredentials = boundToCredentials;
    }

    /** Whether the link stops working once the account's credentials change (password, address, sign-out-everywhere). */
    public boolean boundToCredentials() {
        return boundToCredentials;
    }

    public Duration validity() {
        return validity;
    }
}
