package pl.macieserafin.hourforge.entrytemplate;

import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.timeentry.IssueLink;
import pl.macieserafin.hourforge.user.UserRepository;

@Service
public class EntryTemplateService {
    static final int MAX_PER_TEAM = 30;

    private final EntryTemplateRepository templates;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final ProjectRepository projects;
    private final UserRepository users;

    public EntryTemplateService(EntryTemplateRepository templates, TeamRepository teams, TeamMemberRepository members,
                                ProjectRepository projects, UserRepository users) {
        this.templates = templates;
        this.teams = teams;
        this.members = members;
        this.projects = projects;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(UUID userId, UUID teamId) {
        requireMember(userId, teamId);
        return templates.findByUserIdAndTeamIdOrderByNameAsc(userId, teamId).stream().map(TemplateResponse::of).toList();
    }

    /** The link a template has now, so the caller can keep it without asking Jira again. Null for a missing template. */
    @Transactional(readOnly = true)
    public IssueLink currentLink(UUID userId, UUID templateId) {
        return templates.findByIdAndUserId(templateId, userId)
                .filter(template -> template.getJiraIssueId() != null)
                .map(template -> new IssueLink(template.getJiraConnectionId(), template.getJiraIssueId(), template.getJiraIssueKey()))
                .orElse(null);
    }

    @Transactional
    public TemplateResponse create(UUID userId, UUID teamId, TemplateRequest request, IssueLink link) {
        var team = lockedTeam(userId, teamId);
        requireUsableProject(teamId, request.projectId());
        if (templates.countByUserIdAndTeamId(userId, teamId) >= MAX_PER_TEAM) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Too many templates in this team");
        }
        if (templates.existsByUserIdAndTeamIdAndNameIgnoreCase(userId, teamId, request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A template with this name already exists");
        }
        var template = new EntryTemplate(users.getReferenceById(userId), team);
        template.apply(request.name(), request.projectId(), request.description(), link);
        return save(template, link);
    }

    @Transactional
    public TemplateResponse update(UUID userId, UUID teamId, UUID templateId, TemplateRequest request, IssueLink link) {
        lockedTeam(userId, teamId);
        var template = templates.findByIdAndUserId(templateId, userId).filter(found -> found.getTeam().getId().equals(teamId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireUsableProject(teamId, request.projectId());
        if (!template.getName().equalsIgnoreCase(request.name())
                && templates.existsByUserIdAndTeamIdAndNameIgnoreCase(userId, teamId, request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A template with this name already exists");
        }
        template.apply(request.name(), request.projectId(), request.description(), link);
        return save(template, link);
    }

    @Transactional
    public void delete(UUID userId, UUID teamId, UUID templateId) {
        requireMember(userId, teamId);
        var template = templates.findByIdAndUserId(templateId, userId).filter(found -> found.getTeam().getId().equals(teamId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        templates.delete(template);
        templates.flush();
    }

    private TemplateResponse save(EntryTemplate template, IssueLink link) {
        try {
            return TemplateResponse.of(templates.saveAndFlush(template));
        } catch (DataIntegrityViolationException exception) {
            if (link != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "The Jira site of this team changed; choose the issue again");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A template with this name already exists");
        }
    }

    private void requireMember(UUID userId, UUID teamId) {
        if (!users.existsById(userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        members.findByTeamIdAndUserId(teamId, userId).filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** Same team lock as project and time writes, so a template cannot point at a project that is being removed. */
    private pl.macieserafin.hourforge.team.Team lockedTeam(UUID userId, UUID teamId) {
        requireMember(userId, teamId);
        var team = teams.findLockedById(teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (team.isArchived()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived teams cannot be changed");
        return team;
    }

    private void requireUsableProject(UUID teamId, UUID projectId) {
        var project = projects.findByIdAndTeamId(projectId, teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (project.getArchivedAt() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived projects cannot be used in templates");
    }
}
