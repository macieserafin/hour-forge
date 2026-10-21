package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record StartTimerRequest(@NotNull UUID teamId, @NotNull UUID projectId, @Size(max = 2000) String description,
                                @Pattern(regexp = "[0-9]{1,20}") String jiraIssueId, UUID tagId) {
    public StartTimerRequest(UUID teamId, UUID projectId, String description) {
        this(teamId, projectId, description, null, null);
    }

    public StartTimerRequest(UUID teamId, UUID projectId, String description, String jiraIssueId) {
        this(teamId, projectId, description, jiraIssueId, null);
    }

    public StartTimerRequest {
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
