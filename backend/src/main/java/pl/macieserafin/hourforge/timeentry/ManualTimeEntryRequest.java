package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record ManualTimeEntryRequest(@NotNull UUID teamId, @NotNull UUID projectId,
                                     LocalDate workDate, @NotNull @Positive Integer durationSeconds,
                                     @Size(max = 2000) String description,
                                     @Pattern(regexp = "[0-9]{1,20}") String jiraIssueId) {
    public ManualTimeEntryRequest(UUID teamId, UUID projectId, LocalDate workDate, Integer durationSeconds, String description) {
        this(teamId, projectId, workDate, durationSeconds, description, null);
    }

    public ManualTimeEntryRequest {
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
