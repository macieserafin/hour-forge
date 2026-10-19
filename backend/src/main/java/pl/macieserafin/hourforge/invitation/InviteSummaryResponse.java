package pl.macieserafin.hourforge.invitation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InviteSummaryResponse(UUID id, Instant createdAt, Instant expiresAt, Integer maxUses,
                                    int usedCount, Instant revokedAt, Status status) {
    public enum Status { ACTIVE, EXPIRED, EXHAUSTED, REVOKED }
    public record Page(List<InviteSummaryResponse> content, int page, int size, long totalElements, int totalPages) {}
}
