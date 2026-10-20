package pl.macieserafin.hourforge.preferences;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

/** One person's settings; the defaults are what a person without a stored row has. */
@Entity
@Table(name = "user_preferences")
public class UserPreferences {
    public enum StartPage { DASHBOARD, TIME, PROJECTS, TEAMS }

    public enum TimeFormat { CLOCK, DECIMAL }

    public enum WeekStart { MONDAY, SUNDAY }

    public static final int DEFAULT_LONG_TIMER_HOURS = 10;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "default_team_id")
    private UUID defaultTeamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_page", nullable = false)
    private StartPage startPage = StartPage.DASHBOARD;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_format", nullable = false)
    private TimeFormat timeFormat = TimeFormat.CLOCK;

    @Enumerated(EnumType.STRING)
    @Column(name = "week_start", nullable = false)
    private WeekStart weekStart = WeekStart.MONDAY;

    @Column(name = "long_timer_email", nullable = false)
    private boolean longTimerEmail = true;

    @Column(name = "browser_notifications", nullable = false)
    private boolean browserNotifications = false;

    @Column(name = "long_timer_hours", nullable = false)
    private int longTimerHours = DEFAULT_LONG_TIMER_HOURS;

    @Column(name = "title_timer", nullable = false)
    private boolean titleTimer = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPreferences() {
    }

    public UserPreferences(UUID userId) {
        this.userId = userId;
    }

    public void apply(UUID defaultTeamId, StartPage startPage, TimeFormat timeFormat, WeekStart weekStart, boolean longTimerEmail,
                      boolean browserNotifications, int longTimerHours, boolean titleTimer) {
        this.defaultTeamId = defaultTeamId;
        this.startPage = startPage;
        this.timeFormat = timeFormat;
        this.weekStart = weekStart;
        this.longTimerEmail = longTimerEmail;
        this.browserNotifications = browserNotifications;
        this.longTimerHours = longTimerHours;
        this.titleTimer = titleTimer;
    }

    public UUID getUserId() { return userId; }
    public UUID getDefaultTeamId() { return defaultTeamId; }
    public StartPage getStartPage() { return startPage; }
    public TimeFormat getTimeFormat() { return timeFormat; }
    public WeekStart getWeekStart() { return weekStart; }
    public boolean isLongTimerEmail() { return longTimerEmail; }
    public boolean isBrowserNotifications() { return browserNotifications; }
    public int getLongTimerHours() { return longTimerHours; }
    public boolean isTitleTimer() { return titleTimer; }
}
