package pl.macieserafin.hourforge.jira;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pl.macieserafin.hourforge.user.User;

/** One person's consent to read one Jira site. The tokens are stored encrypted and never leave the backend. */
@Entity
@Table(name = "jira_user_authorizations")
public class JiraUserAuthorization {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "cloud_id", nullable = false)
    private String cloudId;

    @Column(name = "encrypted_access_token", nullable = false)
    private String encryptedAccessToken;

    @Column(name = "encrypted_refresh_token")
    private String encryptedRefreshToken;

    @Column(name = "access_token_expires_at", nullable = false)
    private Instant accessTokenExpiresAt;

    @Column(name = "granted_scopes", nullable = false)
    private String grantedScopes;

    @Column(name = "encryption_key_version", nullable = false)
    private String encryptionKeyVersion;

    @CreationTimestamp
    @Column(name = "connected_at", nullable = false, updatable = false)
    private Instant connectedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JiraUserAuthorization() {
    }

    public JiraUserAuthorization(User user, String cloudId, String encryptedAccessToken, String encryptedRefreshToken,
                                 Instant accessTokenExpiresAt, String grantedScopes, String encryptionKeyVersion) {
        this.user = user;
        this.cloudId = cloudId;
        update(encryptedAccessToken, encryptedRefreshToken, accessTokenExpiresAt, grantedScopes, encryptionKeyVersion);
    }

    /** Replaces the stored tokens, for example after a refresh; the new refresh token replaces the old one. */
    public void update(String encryptedAccessToken, String encryptedRefreshToken, Instant accessTokenExpiresAt,
                       String grantedScopes, String encryptionKeyVersion) {
        this.encryptedAccessToken = encryptedAccessToken;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.grantedScopes = grantedScopes;
        this.encryptionKeyVersion = encryptionKeyVersion;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getCloudId() { return cloudId; }
    public String getEncryptedAccessToken() { return encryptedAccessToken; }
    public String getEncryptedRefreshToken() { return encryptedRefreshToken; }
    public Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public String getGrantedScopes() { return grantedScopes; }
    public String getEncryptionKeyVersion() { return encryptionKeyVersion; }
    public Instant getConnectedAt() { return connectedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
