package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TimerStateRequest(@NotNull UUID entryId, @Size(max = 2000) String description) {
    public TimerStateRequest {
        description = description == null ? null : description.strip();
    }
}
