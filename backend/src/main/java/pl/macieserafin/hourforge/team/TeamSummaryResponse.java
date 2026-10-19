package pl.macieserafin.hourforge.team;

public record TeamSummaryResponse(long activeMembers, long projects, long timeEntries, boolean activeTimers) {
}
