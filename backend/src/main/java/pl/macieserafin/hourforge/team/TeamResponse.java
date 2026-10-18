package pl.macieserafin.hourforge.team;

import java.time.Instant;
import java.util.UUID;

public record TeamResponse(UUID id, String name, String timezone, Instant createdAt, TeamRole role) {
}
