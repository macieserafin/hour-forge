package pl.macieserafin.hourforge.preferences;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PreferencesRequest(UUID defaultTeamId, @NotNull UserPreferences.StartPage startPage,
                                 @NotNull UserPreferences.TimeFormat timeFormat, @NotNull UserPreferences.WeekStart weekStart,
                                 boolean longTimerEmail, boolean browserNotifications,
                                 @Min(4) @Max(24) int longTimerHours, boolean titleTimer) {
}
