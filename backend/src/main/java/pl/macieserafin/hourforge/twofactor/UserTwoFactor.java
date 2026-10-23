package pl.macieserafin.hourforge.twofactor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** The authenticator-app secret of one person; {@code enabledAt} is null until a first code confirmed the setup. */
@Entity
@Table(name = "user_two_factor")
public class UserTwoFactor {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    /** Encrypted with {@code TokenCipher}; never returned once the setup is confirmed. */
    @Column(name = "secret", nullable = false)
    private String secret;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    @Column(name = "last_used_step")
    private Long lastUsedStep;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserTwoFactor() {
    }

    public UserTwoFactor(UUID userId, String secret, Instant createdAt) {
        this.userId = userId;
        this.secret = secret;
        this.createdAt = createdAt;
    }

    public UUID getUserId() { return userId; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public Instant getEnabledAt() { return enabledAt; }
    public boolean isEnabled() { return enabledAt != null; }
    public void enable(Instant at) { this.enabledAt = at; }
    public Long getLastUsedStep() { return lastUsedStep; }
    public void setLastUsedStep(Long lastUsedStep) { this.lastUsedStep = lastUsedStep; }
}
