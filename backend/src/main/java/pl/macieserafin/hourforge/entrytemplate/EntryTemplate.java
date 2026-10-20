package pl.macieserafin.hourforge.entrytemplate;

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
import pl.macieserafin.hourforge.timeentry.IssueLink;
import pl.macieserafin.hourforge.user.User;

/** A person's reusable starting point for an entry in one team: project, note and optionally a Jira issue. */
@Entity
@Table(name = "entry_templates")
public class EntryTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false, updatable = false)
    private Team team;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "jira_connection_id")
    private UUID jiraConnectionId;

    @Column(name = "jira_issue_id")
    private String jiraIssueId;

    @Column(name = "jira_issue_key")
    private String jiraIssueKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EntryTemplate() {
    }

    public EntryTemplate(User user, Team team) {
        this.user = user;
        this.team = team;
    }

    public void apply(String name, UUID projectId, String description, IssueLink link) {
        this.name = name;
        this.projectId = projectId;
        this.description = description;
        this.jiraConnectionId = link == null ? null : link.connectionId();
        this.jiraIssueId = link == null ? null : link.issueId();
        this.jiraIssueKey = link == null ? null : link.issueKey();
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public Team getTeam() { return team; }
    public String getName() { return name; }
    public UUID getProjectId() { return projectId; }
    public String getDescription() { return description; }
    public UUID getJiraConnectionId() { return jiraConnectionId; }
    public String getJiraIssueId() { return jiraIssueId; }
    public String getJiraIssueKey() { return jiraIssueKey; }
}
