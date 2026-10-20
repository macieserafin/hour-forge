package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateTimeEntryRequest(@NotNull UUID projectId, LocalDate workDate,
                                     @NotNull @Positive @Digits(integer = 10, fraction = 0)
                                     @DecimalMax("2147483647") BigDecimal durationSeconds,
                                     @Size(max = 2000) String description,
                                     @Pattern(regexp = "[0-9]{1,20}") String jiraIssueId) {
    public UpdateTimeEntryRequest(UUID projectId, LocalDate workDate, BigDecimal durationSeconds, String description) {
        this(projectId, workDate, durationSeconds, description, null);
    }

    public UpdateTimeEntryRequest {
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
