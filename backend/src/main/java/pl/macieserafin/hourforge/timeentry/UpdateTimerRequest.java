package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Changes what a running (or paused) timer is for without stopping it. A missing issue id removes the Jira link. */
public record UpdateTimerRequest(@NotNull UUID entryId, @NotNull UUID projectId, @Size(max = 2000) String description,
                                 @Pattern(regexp = "[0-9]{1,20}") String jiraIssueId, UUID tagId) {
    public UpdateTimerRequest(UUID entryId, UUID projectId, String description, String jiraIssueId) {
        this(entryId, projectId, description, jiraIssueId, null);
    }
}
