package pl.macieserafin.hourforge.invitation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.PrePersist;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.user.User;

@Entity
@Table(name = "team_invites")
public class TeamInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    public TeamInvite(Team team, User creator, String hash, Integer maxUses, Instant expiresAt, Instant createdAt) {
        this(team, creator, hash, maxUses, expiresAt);
        this.createdAt = createdAt;
    }

    protected TeamInvite() {
    }

    public TeamInvite(Team team, User createdBy, String tokenHash, Integer maxUses, Instant expiresAt) {
        this.team = team;
        this.createdBy = createdBy;
        this.tokenHash = tokenHash;
        this.maxUses = maxUses;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public void setMaxUses(Integer maxUses) {
        this.maxUses = maxUses;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
