package pl.macieserafin.hourforge.team;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.user.UserRepository;

@Service
public class TeamMembershipService {
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final UserRepository users;
    private final Clock clock;

    public TeamMembershipService(TeamRepository teams, TeamMemberRepository members, UserRepository users, Clock clock) {
        this.teams = teams;
        this.members = members;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TeamMembersResponse list(UUID actorId, UUID teamId, boolean includeFormer, int page, int size) {
        requireUser(actorId);
        var actor = activeMember(teamId, actorId);
        if (includeFormer) requireManager(actor);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        var result = members.findMembers(teamId, includeFormer, PageRequest.of(page, size));
        return new TeamMembersResponse(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public void remove(UUID actorId, UUID teamId, UUID targetUserId) {
        requireUser(actorId);
        lockTeam(teamId);
        var actor = activeMember(teamId, actorId);
        requireManager(actor);
        var target = members.findByTeamIdAndUserId(teamId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Admins look after members; only the owner may end another admin's access.
        if (actor.getRole() == TeamRole.ADMIN && target.getRole() != TeamRole.MEMBER && target.getLeftAt() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        endMembership(target, true);
    }

    @Transactional
    public void leave(UUID actorId, UUID teamId) {
        requireUser(actorId);
        lockTeam(teamId);
        endMembership(activeMember(teamId, actorId), false);
    }

    private void endMembership(TeamMember member, boolean removed) {
        if (member.getRole() == TeamRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The owner cannot leave or be removed");
        }
        if (member.getLeftAt() == null) {
            member.setLeftAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
            member.setRemovedAt(removed ? member.getLeftAt() : null);
            members.save(member);
        }
    }

    private void lockTeam(UUID teamId) {
        // Shares the team lock with invitations and time writes; no user lock is taken after it.
        teams.findLockedById(teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void requireUser(UUID actorId) {
        if (!users.existsById(actorId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    private TeamMember activeMember(UUID teamId, UUID userId) {
        return members.findByTeamIdAndUserId(teamId, userId).filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void requireManager(TeamMember member) {
        if (!member.getRole().canManage()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
