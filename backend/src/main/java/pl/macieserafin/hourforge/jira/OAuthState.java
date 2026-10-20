package pl.macieserafin.hourforge.jira;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import pl.macieserafin.hourforge.user.User;

/** A one-time, short-lived OAuth {@code state}. Only hashes are stored; the raw value exists in the redirect alone. */
@Entity
@Table(name = "oauth_states")
public class OAuthState {
    public enum Intent {
        /** The team owner is choosing the team's Jira site. */
        SITE,
        /** A member is connecting their own account to the team's site. */
        MEMBER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "state_hash", nullable = false, updatable = false)
    private String stateHash;

    @Column(name = "session_hash", nullable = false, updatable = false)
    private String sessionHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent", nullable = false, updatable = false)
    private Intent intent;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OAuthState() {
    }

    public OAuthState(String stateHash, String sessionHash, User user, UUID teamId, Intent intent, Instant expiresAt) {
        this.stateHash = stateHash;
        this.sessionHash = sessionHash;
        this.user = user;
        this.teamId = teamId;
        this.intent = intent;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public String getStateHash() { return stateHash; }
    public String getSessionHash() { return sessionHash; }
    public User getUser() { return user; }
    public UUID getTeamId() { return teamId; }
    public Intent getIntent() { return intent; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }

    public void markUsed(Instant now) { this.usedAt = now; }
}
