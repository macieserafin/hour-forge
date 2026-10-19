package pl.macieserafin.hourforge.invitation;

import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record CreateInviteRequest(Instant expiresAt, @Positive Integer maxUses) {
}
