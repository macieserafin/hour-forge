package pl.macieserafin.hourforge.team;

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
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "active_timers_visible", nullable = false)
    private boolean activeTimersVisible = true;

    @Column(name = "require_description", nullable = false)
    private boolean requireDescription;

    @Column(name = "require_tag_or_issue", nullable = false)
    private boolean requireTagOrIssue;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "week_start", nullable = false, length = 10)
    private pl.macieserafin.hourforge.preferences.UserPreferences.WeekStart weekStart = pl.macieserafin.hourforge.preferences.UserPreferences.WeekStart.MONDAY;

    public boolean isRequireDescription() { return requireDescription; }
    public void setRequireDescription(boolean requireDescription) { this.requireDescription = requireDescription; }
    public boolean isRequireTagOrIssue() { return requireTagOrIssue; }
    public void setRequireTagOrIssue(boolean requireTagOrIssue) { this.requireTagOrIssue = requireTagOrIssue; }
    public pl.macieserafin.hourforge.preferences.UserPreferences.WeekStart getWeekStart() { return weekStart; }
    public void setWeekStart(pl.macieserafin.hourforge.preferences.UserPreferences.WeekStart weekStart) { this.weekStart = weekStart; }

    @Column(name = "weekly_report_enabled", nullable = false)
    private boolean weeklyReportEnabled;

    @Column(name = "weekly_report_sent_for")
    private java.time.LocalDate weeklyReportSentFor;

    public boolean isWeeklyReportEnabled() { return weeklyReportEnabled; }
    public void setWeeklyReportEnabled(boolean weeklyReportEnabled) { this.weeklyReportEnabled = weeklyReportEnabled; }
    public java.time.LocalDate getWeeklyReportSentFor() { return weeklyReportSentFor; }
    public void setWeeklyReportSentFor(java.time.LocalDate weeklyReportSentFor) { this.weeklyReportSentFor = weeklyReportSentFor; }

    protected Team() {
    }

    public Team(String name, String timezone) {
        this.name = name;
        this.timezone = timezone;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public boolean isActiveTimersVisible() {
        return activeTimersVisible;
    }

    public void setActiveTimersVisible(boolean activeTimersVisible) {
        this.activeTimersVisible = activeTimersVisible;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
