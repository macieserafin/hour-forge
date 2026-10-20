package pl.macieserafin.hourforge.entrytemplate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TemplateRequest(@NotBlank @Size(max = 80) String name, @NotNull UUID projectId, @Size(max = 2000) String description,
                              @Pattern(regexp = "[0-9]{1,20}") String jiraIssueId) {
    public TemplateRequest {
        name = name == null ? null : name.strip();
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
