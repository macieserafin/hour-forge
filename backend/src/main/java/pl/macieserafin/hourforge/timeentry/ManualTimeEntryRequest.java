package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

public record ManualTimeEntryRequest(@NotNull UUID teamId, @NotNull UUID projectId,
                                     @NotNull LocalDate workDate, @NotNull @Positive Integer durationSeconds,
                                     String description) {
    public ManualTimeEntryRequest {
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
