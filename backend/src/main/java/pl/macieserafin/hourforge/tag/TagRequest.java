package pl.macieserafin.hourforge.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pl.macieserafin.hourforge.project.ProjectColor;

public record TagRequest(@NotBlank @Size(max = 40) String name, ProjectColor color) {
    public TagRequest {
        name = name == null ? null : name.strip();
    }
}
