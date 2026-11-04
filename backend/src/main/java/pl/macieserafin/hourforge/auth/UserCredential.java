package pl.macieserafin.hourforge.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import pl.macieserafin.hourforge.user.User;

import java.util.UUID;

@Entity
@Table(name = "user_credentials")
public class UserCredential {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Null for an account that signs in only through a linked provider; the row stays for the session version.
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "session_version", nullable = false)
    private int sessionVersion;

    protected UserCredential() {
    }

    public UserCredential(User user, String passwordHash) {
        this.user = user;
        this.passwordHash = passwordHash;
    }

    /** The credential of an account without a password; one can be set later through the password reset. */
    public static UserCredential withoutPassword(User user) {
        return new UserCredential(user, null);
    }

    public UUID getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public int getSessionVersion() {
        return sessionVersion;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Invalidates every session issued before this call. */
    public int invalidateSessions() {
        return ++sessionVersion;
    }
}
