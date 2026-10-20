package pl.macieserafin.hourforge.security;

import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.UUID;

public class AppUserPrincipal extends User {
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    // Null only for principals built without a stored credential (tests); real logins always carry the version.
    private final Integer sessionVersion;

    public AppUserPrincipal(UUID userId, String email, String passwordHash) {
        this(userId, email, passwordHash, null);
    }

    public AppUserPrincipal(UUID userId, String email, String passwordHash, Integer sessionVersion) {
        super(email, passwordHash, List.of());
        this.userId = userId;
        this.sessionVersion = sessionVersion;
    }

    public UUID getUserId() {
        return userId;
    }

    public Integer getSessionVersion() {
        return sessionVersion;
    }
}
