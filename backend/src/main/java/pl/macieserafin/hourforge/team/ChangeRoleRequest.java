package pl.macieserafin.hourforge.team;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull TeamRole role) {
}
