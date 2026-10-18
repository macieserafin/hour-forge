package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StopTimerRequest(@NotNull UUID entryId, String description) {
    public StopTimerRequest {
        description = description == null ? null : description.strip();
    }
}
