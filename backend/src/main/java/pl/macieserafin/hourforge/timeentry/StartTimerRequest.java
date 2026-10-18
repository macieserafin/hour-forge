package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartTimerRequest(@NotNull UUID teamId, @NotNull UUID projectId, String description) {
    public StartTimerRequest {
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
