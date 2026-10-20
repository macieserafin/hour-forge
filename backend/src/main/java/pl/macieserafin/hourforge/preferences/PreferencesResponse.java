package pl.macieserafin.hourforge.preferences;

import java.util.UUID;

public record PreferencesResponse(UUID defaultTeamId, UserPreferences.StartPage startPage, UserPreferences.TimeFormat timeFormat,
                                  UserPreferences.WeekStart weekStart, boolean longTimerEmail, boolean browserNotifications,
                                  int longTimerHours, boolean titleTimer) {
}
