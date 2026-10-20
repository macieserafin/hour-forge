package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/** Copies the person's own entries of one day in a team to another day, as manual entries. */
public record CopyDayRequest(@NotNull UUID teamId, @NotNull LocalDate fromDate, @NotNull LocalDate toDate) {
}
