package pl.macieserafin.hourforge.invitation;

import java.time.Instant;
import java.util.UUID;

public record InviteResponse(UUID id, UUID teamId, String invitationUrl, String token,
                             Instant expiresAt, Integer maxUses) {
}
