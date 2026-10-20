package pl.macieserafin.hourforge.auth;

import java.time.Duration;

public enum EmailTokenPurpose {
    VERIFY_EMAIL(Duration.ofHours(24)),
    RESET_PASSWORD(Duration.ofHours(1)),
    CHANGE_EMAIL(Duration.ofHours(24));

    private final Duration validity;

    EmailTokenPurpose(Duration validity) {
        this.validity = validity;
    }

    public Duration validity() {
        return validity;
    }
}
