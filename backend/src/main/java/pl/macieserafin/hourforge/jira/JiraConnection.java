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
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.user.User;

/** The Jira Cloud site a team works with. Chosen by the team owner; members connect their own accounts to it. */
@Entity
@Table(name = "jira_connections")
public class JiraConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "cloud_id", nullable = false)
    private String cloudId;

    @Column(name = "site_url", nullable = false)
    private String siteUrl;

    @Column(name = "site_name", nullable = false)
    private String siteName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connected_by", nullable = false)
    private User connectedBy;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JiraConnection() {
    }

    public JiraConnection(Team team, String cloudId, String siteUrl, String siteName, User connectedBy) {
        this.team = team;
        this.cloudId = cloudId;
        this.siteUrl = siteUrl;
        this.siteName = siteName;
        this.connectedBy = connectedBy;
    }

    public UUID getId() { return id; }
    public Team getTeam() { return team; }
    public String getCloudId() { return cloudId; }
    public String getSiteUrl() { return siteUrl; }
    public String getSiteName() { return siteName; }
    public User getConnectedBy() { return connectedBy; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
