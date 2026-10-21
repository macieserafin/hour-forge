package pl.macieserafin.hourforge.timeentry;

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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.tag.Tag;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.user.User;

@Entity
@Table(name = "time_entries")
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private EntryType entryType;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "paused_duration_micros", nullable = false)
    private long pausedDurationMicros;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "last_edited_at")
    private Instant lastEditedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_edited_by")
    private User lastEditedBy;

    @Column(name = "long_timer_reminded_at")
    private Instant longTimerRemindedAt;

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

    protected TimeEntry() {
    }

    public TimeEntry(User user, Team team, Project project, EntryType entryType, LocalDate workDate, Instant startedAt, Instant endedAt, Integer durationSeconds, String description) {
        this.user = user;
        this.team = team;
        this.project = project;
        this.entryType = entryType;
        this.workDate = workDate;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public Instant getLastEditedAt() {
        return lastEditedAt;
    }

    public void setLastEditedAt(Instant lastEditedAt) {
        this.lastEditedAt = lastEditedAt;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

    public User getLastEditedBy() {
        return lastEditedBy;
    }

    public void setLastEditedBy(User lastEditedBy) {
        this.lastEditedBy = lastEditedBy;
    }

    /** The name of whoever corrected the entry, or null when only its owner changed it. */
    public String correctedByName() {
        return lastEditedBy == null || lastEditedBy.getId().equals(user.getId()) ? null : lastEditedBy.getDisplayName();
    }

    public Instant getLongTimerRemindedAt() {
        return longTimerRemindedAt;
    }

    public void setLongTimerRemindedAt(Instant longTimerRemindedAt) {
        this.longTimerRemindedAt = longTimerRemindedAt;
    }

    public UUID getJiraConnectionId() {
        return jiraConnectionId;
    }

    public String getJiraIssueId() {
        return jiraIssueId;
    }

    public String getJiraIssueKey() {
        return jiraIssueKey;
    }

    public void setJiraIssue(IssueLink link) {
        if (link == null) {
            jiraConnectionId = null;
            jiraIssueId = null;
            jiraIssueKey = null;
        } else {
            jiraConnectionId = link.connectionId();
            jiraIssueId = link.issueId();
            jiraIssueKey = link.issueKey();
        }
    }

    public User getUser() {
        return user;
    }

    public Team getTeam() {
        return team;
    }

    public Project getProject() {
        return project;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public void setPausedAt(Instant pausedAt) {
        this.pausedAt = pausedAt;
    }

    public long getPausedDurationMicros() {
        return pausedDurationMicros;
    }

    public void setPausedDurationMicros(long pausedDurationMicros) {
        this.pausedDurationMicros = pausedDurationMicros;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
