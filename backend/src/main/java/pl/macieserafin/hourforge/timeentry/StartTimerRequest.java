package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record StartTimerRequest(@NotNull UUID teamId, @NotNull UUID projectId, @Size(max = 2000) String description) {
    public StartTimerRequest {
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
