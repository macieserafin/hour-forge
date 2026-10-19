package pl.macieserafin.hourforge.team;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferOwnershipRequest(@NotNull UUID userId) {
}
