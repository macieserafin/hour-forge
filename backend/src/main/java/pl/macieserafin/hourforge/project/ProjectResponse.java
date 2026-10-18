package pl.macieserafin.hourforge.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(UUID id, UUID teamId, String name, String description,
                              Instant createdAt, Instant archivedAt) {
}
