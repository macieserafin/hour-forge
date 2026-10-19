package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateTimeEntryRequest(@NotNull UUID projectId, @NotNull LocalDate workDate,
                                     @NotNull @Positive @Digits(integer = 10, fraction = 0)
                                     @DecimalMax("2147483647") BigDecimal durationSeconds, String description) {
    public UpdateTimeEntryRequest {
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
