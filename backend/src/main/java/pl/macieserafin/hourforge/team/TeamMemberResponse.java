package pl.macieserafin.hourforge.team;

import java.time.Instant;
import java.util.UUID;

/** A member of a team. The job title and part of a full-time job are only filled in for the owner, the admins and the person. */
public record TeamMemberResponse(UUID userId, String displayName, TeamRole role, Instant joinedAt, Instant leftAt,
                                 String jobTitle, Integer ftePercent) {
    public TeamMemberResponse withoutProfile() {
        return new TeamMemberResponse(userId, displayName, role, joinedAt, leftAt, null, null);
    }
}
