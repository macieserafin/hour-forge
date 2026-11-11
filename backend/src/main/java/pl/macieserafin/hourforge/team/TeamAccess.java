package pl.macieserafin.hourforge.team;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Who may see and change a team, checked the same way everywhere. A stranger and a former member both get 404, so the
 * answer never tells whether a team exists; a member without the role for a change gets 403.
 */
public final class TeamAccess {
    private TeamAccess() {
    }

    /** The person's membership while it lasts. */
    public static TeamMember member(TeamMemberRepository members, UUID teamId, UUID userId) {
        return members.findByTeamIdAndUserId(teamId, userId).filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** As {@link #member}, for someone who runs the team: its owner or an admin. */
    public static TeamMember manager(TeamMemberRepository members, UUID teamId, UUID userId) {
        var member = member(members, teamId, userId);
        if (!member.getRole().canManage()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return member;
    }
}
