package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TimerStateRequest(@NotNull UUID entryId, String description) {
    public TimerStateRequest {
        description = description == null ? null : description.strip();
    }
}
