package pl.macieserafin.hourforge.timeentry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Stops the running timer and starts a new one as one step: either both happen or neither. */
public record SwitchTimerRequest(@NotNull @Valid StopTimerRequest stop, @NotNull @Valid StartTimerRequest start) {
}
