package pl.macieserafin.hourforge.security;

import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.UUID;

public class AppUserPrincipal extends User {
    private static final long serialVersionUID = 1L;

    private final UUID userId;

    public AppUserPrincipal(UUID userId, String email, String passwordHash) {
        super(email, passwordHash, List.of());
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}
