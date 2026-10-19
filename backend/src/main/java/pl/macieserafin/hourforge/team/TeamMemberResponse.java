package pl.macieserafin.hourforge.team;

import java.time.Instant;
import java.util.UUID;

public record TeamMemberResponse(UUID userId, String displayName, TeamRole role, Instant joinedAt, Instant leftAt) {
}
