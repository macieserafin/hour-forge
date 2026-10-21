package pl.macieserafin.hourforge.team;

import java.time.Instant;
import java.util.UUID;
import pl.macieserafin.hourforge.preferences.UserPreferences.WeekStart;

public record TeamResponse(UUID id, String name, String timezone, Instant createdAt, TeamRole role, Instant archivedAt,
                           boolean activeTimersVisible, boolean requireDescription, boolean requireTagOrIssue, WeekStart weekStart, boolean weeklyReport,
                           pl.macieserafin.hourforge.project.ProjectColor color) {
    public static TeamResponse of(Team team, TeamRole role) {
        return new TeamResponse(team.getId(), team.getName(), team.getTimezone(), team.getCreatedAt(), role, team.getArchivedAt(),
                team.isActiveTimersVisible(), team.isRequireDescription(), team.isRequireTagOrIssue(), team.getWeekStart(), team.isWeeklyReportEnabled(), team.getColor());
    }
}
