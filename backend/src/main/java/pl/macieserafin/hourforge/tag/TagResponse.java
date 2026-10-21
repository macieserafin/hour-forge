package pl.macieserafin.hourforge.tag;

import java.time.Instant;
import java.util.UUID;
import pl.macieserafin.hourforge.project.ProjectColor;

public record TagResponse(UUID id, UUID teamId, String name, ProjectColor color, Instant archivedAt) {
    static TagResponse of(Tag tag) {
        return new TagResponse(tag.getId(), tag.getTeam().getId(), tag.getName(), tag.getColor(), tag.getArchivedAt());
    }
}
