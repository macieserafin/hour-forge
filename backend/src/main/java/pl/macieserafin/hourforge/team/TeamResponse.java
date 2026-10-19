package pl.macieserafin.hourforge.team;

import java.time.Instant;
import java.util.UUID;

public record TeamResponse(UUID id, String name, String timezone, Instant createdAt, TeamRole role, Instant archivedAt) {
    public static TeamResponse of(Team team, TeamRole role) {
        return new TeamResponse(team.getId(), team.getName(), team.getTimezone(), team.getCreatedAt(), role, team.getArchivedAt());
    }
}
