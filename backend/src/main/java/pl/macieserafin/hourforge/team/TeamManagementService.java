package pl.macieserafin.hourforge.team;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.invitation.TeamInviteRepository;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Owner operations that change or end an existing team. */
@Service
public class TeamManagementService {
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final TimeEntryRepository entries;
    private final TeamInviteRepository invites;
    private final Clock clock;

    public TeamManagementService(TeamRepository teams, TeamMemberRepository members, UserRepository users,
                                 ProjectRepository projects, TimeEntryRepository entries, TeamInviteRepository invites,
                                 Clock clock) {
        this.teams = teams;
        this.members = members;
        this.users = users;
        this.projects = projects;
        this.entries = entries;
        this.invites = invites;
        this.clock = clock;
    }

    @Transactional
    public TeamResponse update(UUID actorId, UUID teamId, CreateTeamRequest request) {
        var team = ownedTeam(actorId, teamId);
        requireNotArchived(team);
        // Stored work dates keep their original team-local meaning; the zone applies to new entries only.
        team.setName(request.name());
        team.setTimezone(request.timezone());
        return TeamResponse.of(teams.saveAndFlush(team), TeamRole.OWNER);
    }

    @Transactional
    public void transferOwnership(UUID actorId, UUID teamId, UUID targetUserId) {
        var team = ownedTeam(actorId, teamId);
        if (actorId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose another active member");
        }
        var target = members.findByTeamIdAndUserId(team.getId(), targetUserId)
                .filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var owner = activeMember(team.getId(), actorId);
        // Demote first: the partial unique index allows exactly one active owner at any moment.
        owner.setRole(TeamRole.MEMBER);
        members.saveAndFlush(owner);
        target.setRole(TeamRole.OWNER);
        members.saveAndFlush(target);
    }

    @Transactional
    public TeamResponse archive(UUID actorId, UUID teamId) {
        var team = ownedTeam(actorId, teamId);
        if (!team.isArchived()) {
            requireNoActiveTimers(team, "Stop active timers before archiving the team");
            team.setArchivedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
            teams.saveAndFlush(team);
        }
        return TeamResponse.of(team, TeamRole.OWNER);
    }

    @Transactional(readOnly = true)
    public TeamSummaryResponse summary(UUID actorId, UUID teamId) {
        // Read-only: no row lock (SELECT ... FOR UPDATE is rejected in a read-only transaction).
        var team = ownedTeam(actorId, teamId, false);
        return new TeamSummaryResponse(members.countByTeamIdAndLeftAtIsNull(team.getId()), projects.countByTeamId(team.getId()),
                entries.countByTeamId(team.getId()),
                entries.existsByTeamIdAndEntryTypeAndEndedAtIsNull(team.getId(), EntryType.TIMER));
    }

    @Transactional
    public void delete(UUID actorId, UUID teamId) {
        var team = ownedTeam(actorId, teamId);
        requireNoActiveTimers(team, "Stop active timers before deleting the team");
        // Children first; the team row stays locked until the transaction commits.
        entries.deleteAllByTeamId(team.getId());
        projects.deleteAllByTeamId(team.getId());
        invites.deleteAllByTeamId(team.getId());
        members.deleteAllByTeamId(team.getId());
        teams.deleteTeamById(team.getId());
    }

    private Team ownedTeam(UUID actorId, UUID teamId) {
        return ownedTeam(actorId, teamId, true);
    }

    private Team ownedTeam(UUID actorId, UUID teamId, boolean lock) {
        if (!users.existsById(actorId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        // Writes take the same team lock as membership, project and time writes, so checks and changes are serialized.
        var team = (lock ? teams.findLockedById(teamId) : teams.findById(teamId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (activeMember(teamId, actorId).getRole() != TeamRole.OWNER) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return team;
    }

    private TeamMember activeMember(UUID teamId, UUID userId) {
        return members.findByTeamIdAndUserId(teamId, userId).filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void requireNoActiveTimers(Team team, String message) {
        if (entries.existsByTeamIdAndEntryTypeAndEndedAtIsNull(team.getId(), EntryType.TIMER)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    static void requireNotArchived(Team team) {
        if (team.isArchived()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived teams cannot be changed");
    }
}
