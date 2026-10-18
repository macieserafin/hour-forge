package pl.macieserafin.hourforge.invitation;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record CreateInviteRequest(@NotNull @Future Instant expiresAt, @Positive Integer maxUses) {
}
