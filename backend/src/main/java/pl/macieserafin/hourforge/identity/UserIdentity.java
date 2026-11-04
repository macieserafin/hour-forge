package pl.macieserafin.hourforge.identity;

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
import pl.macieserafin.hourforge.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * An account linked to an external sign-in provider. The account is found only by the provider's subject, compared
 * exactly (Google's {@code sub} is case-sensitive); the e-mail is kept for display and never selects an account.
 */
@Entity
@Table(name = "user_identities")
public class UserIdentity {
    static final int MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false)
    private IdentityProvider provider;

    @Column(name = "subject", nullable = false, updatable = false)
    private String subject;

    @Column(name = "email")
    private String email;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected UserIdentity() {
    }

    public UserIdentity(User user, IdentityProvider provider, String subject, String email, Instant linkedAt) {
        if (subject == null || subject.isEmpty() || subject.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("The subject must have 1 to " + MAX_LENGTH + " characters");
        }
        this.user = user;
        this.provider = provider;
        this.subject = subject;
        this.email = checkedEmail(email);
        this.linkedAt = linkedAt;
    }

    /**
     * A sign-in through the provider; the provider's current address replaces the stored one. A sign-in that brings
     * no address keeps the stored one.
     */
    public void recordUse(Instant at, String currentEmail) {
        this.lastUsedAt = at;
        if (currentEmail != null) this.email = checkedEmail(currentEmail);
    }

    private static String checkedEmail(String email) {
        if (email != null && email.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("The e-mail must have at most " + MAX_LENGTH + " characters");
        }
        return email;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public IdentityProvider getProvider() { return provider; }
    public String getSubject() { return subject; }
    public String getEmail() { return email; }
    public Instant getLinkedAt() { return linkedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
}
