package pl.macieserafin.hourforge.team;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.invitation.TeamInviteRepository;
import pl.macieserafin.hourforge.entrytemplate.EntryTemplateRepository;
import pl.macieserafin.hourforge.jira.JiraConnectionRepository;
import pl.macieserafin.hourforge.jira.JiraUserAuthorizationRepository;
import pl.macieserafin.hourforge.jira.OAuthStateRepository;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.tag.TagRepository;
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
    private final TagRepository tags;
    private final TimeEntryRepository entries;
    private final TeamInviteRepository invites;
    private final JiraConnectionRepository jiraConnections;
    private final EntryTemplateRepository entryTemplates;
    private final JiraUserAuthorizationRepository jiraAuthorizations;
    private final OAuthStateRepository oauthStates;
    private final Clock clock;
    private final TeamMailService mail;

    public TeamManagementService(TeamRepository teams, TeamMemberRepository members, UserRepository users,
                                 ProjectRepository projects, TimeEntryRepository entries, TeamInviteRepository invites,
                                 JiraConnectionRepository jiraConnections, JiraUserAuthorizationRepository jiraAuthorizations,
                                 OAuthStateRepository oauthStates, EntryTemplateRepository entryTemplates, TagRepository tags, Clock clock,
                                 TeamMailService mail) {
        this.mail = mail;
        this.teams = teams;
        this.members = members;
        this.users = users;
        this.projects = projects;
        this.tags = tags;
        this.entries = entries;
        this.invites = invites;
        this.jiraConnections = jiraConnections;
        this.entryTemplates = entryTemplates;
        this.jiraAuthorizations = jiraAuthorizations;
        this.oauthStates = oauthStates;
        this.clock = clock;
    }

    @Transactional
    public TeamResponse update(UUID actorId, UUID teamId, CreateTeamRequest request) {
        var team = lockedTeam(teamId);
        var role = managerRole(actorId, teamId);
        requireNotArchived(team);
        // Stored work dates keep their original team-local meaning; the zone applies to new entries only.
        team.setName(request.name());
        team.setTimezone(request.timezone());
        // Absent means unchanged, so older clients that only send name and zone do not reset the setting.
        if (request.activeTimersVisible() != null) team.setActiveTimersVisible(request.activeTimersVisible());
        if (request.requireDescription() != null) team.setRequireDescription(request.requireDescription());
        if (request.weekStart() != null) team.setWeekStart(request.weekStart());
        if (request.requireTagOrIssue() != null) {
            // A rule nobody can satisfy would lock everyone out of saving work: it needs a tag to pick or a Jira site to link to.
            if (request.requireTagOrIssue() && !team.isRequireTagOrIssue() && !tagOrIssueAvailable(teamId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Add a tag or connect Jira before requiring one");
            }
            team.setRequireTagOrIssue(request.requireTagOrIssue());
        }
        return TeamResponse.of(teams.saveAndFlush(team), role);
    }

    /** Only the owner decides who is an admin; the owner role itself moves only by transferring ownership. */
    @Transactional
    public void changeRole(UUID actorId, UUID teamId, UUID targetUserId, TeamRole role) {
        var team = ownedTeam(actorId, teamId);
        requireNotArchived(team);
        if (role == TeamRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use ownership transfer to change the owner");
        }
        if (actorId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose another active member");
        }
        var target = activeMember(teamId, targetUserId);
        if (target.getRole() == TeamRole.OWNER) throw new ResponseStatusException(HttpStatus.CONFLICT, "The owner role cannot be changed");
        boolean wasAdmin = target.getRole() == TeamRole.ADMIN;
        target.setRole(role);
        members.saveAndFlush(target);
        if (wasAdmin != (role == TeamRole.ADMIN)) mail.sendRoleChange(target.getUser(), team.getName(), role == TeamRole.ADMIN);
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
        mail.sendOwnershipChange(target.getUser(), team.getName(), true);
        mail.sendOwnershipChange(owner.getUser(), team.getName(), false);
    }

    @Transactional
    public TeamResponse archive(UUID actorId, UUID teamId) {
        var team = lockedTeam(teamId);
        var role = managerRole(actorId, teamId);
        if (!team.isArchived()) {
            requireNoActiveTimers(team, "Stop active timers before archiving the team");
            team.setArchivedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
            teams.saveAndFlush(team);
            members.findMembers(teamId, false, org.springframework.data.domain.Pageable.unpaged()).forEach(member -> {
                if (!member.userId().equals(actorId)) users.findById(member.userId()).ifPresent(person -> mail.sendArchived(person, team.getName()));
            });
        }
        return TeamResponse.of(team, role);
    }

    @Transactional(readOnly = true)
    public TeamSummaryResponse summary(UUID actorId, UUID teamId) {
        // Read-only: no row lock (SELECT ... FOR UPDATE is rejected in a read-only transaction).
        managerRole(actorId, teamId);
        var team = teams.findById(teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
        entryTemplates.deleteAllByTeamId(team.getId());
        tags.deleteAllByTeamId(team.getId());
        projects.deleteAllByTeamId(team.getId());
        invites.deleteAllByTeamId(team.getId());
        oauthStates.deleteAllByTeamId(team.getId());
        jiraConnections.findByTeamId(team.getId()).ifPresent(connection -> {
            jiraConnections.deleteAllByTeamId(team.getId());
            // Grants for a site are only needed while some team still uses that site.
            if (!jiraConnections.existsByCloudId(connection.getCloudId())) jiraAuthorizations.deleteAllByCloudId(connection.getCloudId());
        });
        members.deleteAllByTeamId(team.getId());
        teams.deleteTeamById(team.getId());
    }

    /** Operations only the owner may do: deleting the team, handing it over and choosing admins. */
    private Team ownedTeam(UUID actorId, UUID teamId) {
        if (!users.existsById(actorId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        // Writes take the same team lock as membership, project and time writes, so checks and changes are serialized.
        var team = lockedTeam(teamId);
        if (activeMember(teamId, actorId).getRole() != TeamRole.OWNER) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return team;
    }

    /** Checks that the actor is an active owner or admin and returns the role. */
    private boolean tagOrIssueAvailable(UUID teamId) {
        return !tags.findByTeamIdAndArchivedAtIsNullOrderByNameAscIdAsc(teamId).isEmpty() || jiraConnections.findByTeamId(teamId).isPresent();
    }

    private TeamRole managerRole(UUID actorId, UUID teamId) {
        if (!users.existsById(actorId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var role = activeMember(teamId, actorId).getRole();
        if (!role.canManage()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return role;
    }

    private Team lockedTeam(UUID teamId) {
        return teams.findLockedById(teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
