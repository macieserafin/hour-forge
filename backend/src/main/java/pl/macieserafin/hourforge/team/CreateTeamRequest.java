package pl.macieserafin.hourforge.team;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.ZoneId;

public record CreateTeamRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String timezone,
        Boolean activeTimersVisible,
        Boolean requireDescription,
        Boolean requireTagOrIssue,
        pl.macieserafin.hourforge.preferences.UserPreferences.WeekStart weekStart,
        Boolean weeklyReport,
        pl.macieserafin.hourforge.project.ProjectColor color,
        Boolean clearColor
) {
    public CreateTeamRequest(String name, String timezone) {
        this(name, timezone, null, null, null, null, null, null, null);
    }

    public CreateTeamRequest(String name, String timezone, Boolean activeTimersVisible) {
        this(name, timezone, activeTimersVisible, null, null, null, null, null, null);
    }

    public CreateTeamRequest {
        name = name == null ? null : name.strip();
        timezone = timezone == null ? null : timezone.strip();
    }

    @AssertTrue(message = "timezone must be a valid IANA time zone")
    public boolean isTimezoneValid() {
        return timezone == null || timezone.isBlank() || ZoneId.getAvailableZoneIds().contains(timezone);
    }
}
