package pl.macieserafin.hourforge.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "blocked_reason")
    private String blockedReason;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    @Column(name = "inactivity_warned_days")
    private Integer inactivityWarnedDays;

    @Column(name = "inactivity_warned_at")
    private Instant inactivityWarnedAt;

    protected User() {
    }

    public User(String email, String displayName) {
        this.email = email;
        this.displayName = displayName;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** The last day (UTC midnight) the person used the app; null for accounts that never signed in since V27. */
    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void setEmailVerifiedAt(Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    /** Removes the personal data but keeps the row, so entries and history stay attached to a placeholder user. */
    public void anonymize(String placeholderEmail, String placeholderName, Instant now) {
        this.email = placeholderEmail;
        this.displayName = placeholderName;
        this.emailVerifiedAt = null;
        this.deletedAt = now;
        this.avatarUpdatedAt = null;
    }

    /** When the profile picture last changed (also its cache key); null when the person has none. */
    public Instant getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public void setAvatarUpdatedAt(Instant avatarUpdatedAt) {
        this.avatarUpdatedAt = avatarUpdatedAt;
    }

    /** The last inactivity warning (30 or 7 days before removal), 0 when kept for the teams it owns, null when none. */
    public Integer getInactivityWarnedDays() {
        return inactivityWarnedDays;
    }

    public Instant getInactivityWarnedAt() {
        return inactivityWarnedAt;
    }

    public void warnAboutInactivity(int days, Instant at) {
        this.inactivityWarnedDays = days;
        this.inactivityWarnedAt = at;
    }

    public Instant getBlockedAt() {
        return blockedAt;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public boolean isBlocked() {
        return blockedAt != null;
    }

    /** Blocks sign-in (with a reason for the operator), or lifts the block when {@code at} is null. */
    public void block(Instant at, String reason) {
        this.blockedAt = at;
        this.blockedReason = at == null ? null : reason;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
