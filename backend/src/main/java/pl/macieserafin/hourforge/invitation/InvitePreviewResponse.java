package pl.macieserafin.hourforge.invitation;

import java.time.Instant;
import java.util.UUID;

public record InvitePreviewResponse(UUID teamId, String teamName, InvitePreviewStatus status, Instant expiresAt) {
}
