package pl.macieserafin.hourforge.project;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final TeamMemberRepository members;
    private final TeamRepository teams;
    private final UserRepository users;
    private final TimeEntryRepository entries;
    private final pl.macieserafin.hourforge.entrytemplate.EntryTemplateRepository entryTemplates;

    public ProjectService(ProjectRepository projects, TeamMemberRepository members, TeamRepository teams,
                          UserRepository users, TimeEntryRepository entries,
                          pl.macieserafin.hourforge.entrytemplate.EntryTemplateRepository entryTemplates) {
        this.projects = projects;
        this.members = members;
        this.teams = teams;
        this.users = users;
        this.entries = entries;
        this.entryTemplates = entryTemplates;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(UUID userId, UUID teamId, boolean includeArchived) {
        requireMember(userId, teamId, false);
        var result = includeArchived ? projects.findByTeamIdOrderByCreatedAtDescIdAsc(teamId)
                : projects.findByTeamIdAndArchivedAtIsNullOrderByCreatedAtDescIdAsc(teamId);
        return result.stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID userId, UUID teamId, UUID projectId) {
        requireMember(userId, teamId, false);
        return response(findProject(teamId, projectId));
    }

    @Transactional
    public ProjectResponse create(UUID userId, UUID teamId, ProjectRequest request) {
        var membership = requireMember(userId, teamId, true);
        var project = new Project(membership.getTeam(), request.name(), request.description());
        project.setRepositoryUrl(request.repositoryUrl());
        project.setColor(request.color());
        project = projects.saveAndFlush(project);
        return response(project);
    }

    @Transactional
    public ProjectResponse update(UUID userId, UUID teamId, UUID projectId, ProjectRequest request) {
        requireMember(userId, teamId, true);
        var project = findProject(teamId, projectId);
        if (project.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived projects cannot be edited");
        }
        project.setName(request.name());
        project.setDescription(request.description());
        project.setRepositoryUrl(request.repositoryUrl());
        project.setColor(request.color());
        return response(projects.saveAndFlush(project));
    }

    @Transactional
    public ProjectResponse archive(UUID userId, UUID teamId, UUID projectId) {
        requireMember(userId, teamId, true);
        var project = findProject(teamId, projectId);
        if (project.getArchivedAt() == null) {
            if (entries.existsByProjectIdAndEntryTypeAndEndedAtIsNull(projectId, EntryType.TIMER)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Stop active timers before archiving the project");
            }
            project.setArchivedAt(Instant.now());
            projects.saveAndFlush(project);
        }
        return response(project);
    }

    @Transactional
    public ProjectResponse restore(UUID userId, UUID teamId, UUID projectId) {
        requireMember(userId, teamId, true);
        var project = findProject(teamId, projectId);
        project.setArchivedAt(null);
        return response(projects.saveAndFlush(project));
    }

    @Transactional
    public void delete(UUID userId, UUID teamId, UUID projectId) {
        requireMember(userId, teamId, true);
        var project = findProject(teamId, projectId);
        if (entries.existsByProjectId(projectId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only empty projects can be deleted");
        }
        // Templates that point at the empty project are of no use any more.
        entryTemplates.deleteAllByProjectId(projectId);
        projects.delete(project);
        projects.flush();
    }

    private TeamMember requireMember(UUID userId, UUID teamId, boolean ownerRequired) {
        if (!users.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        // Use the same team lock as membership changes to serialize project writes and authorization.
        var team = ownerRequired ? teams.findLockedById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)) : null;
        var membership = members.findByTeamIdAndUserId(teamId, userId)
                .filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (ownerRequired && !membership.getRole().canManage()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (team != null && team.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Projects of an archived team cannot be changed");
        }
        return membership;
    }

    private Project findProject(UUID teamId, UUID projectId) {
        return projects.findByIdAndTeamId(projectId, teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ProjectResponse response(Project project) {
        return new ProjectResponse(project.getId(), project.getTeam().getId(), project.getName(),
                project.getDescription(), project.getCreatedAt(), project.getArchivedAt(), project.getRepositoryUrl(), project.getColor());
    }
}
