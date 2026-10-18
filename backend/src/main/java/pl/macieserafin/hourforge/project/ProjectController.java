package pl.macieserafin.hourforge.project;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams/{teamId}/projects")
public class ProjectController {
    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal AppUserPrincipal principal,
                                      @PathVariable UUID teamId,
                                      @RequestParam(defaultValue = "false") boolean includeArchived) {
        return projects.list(principal.getUserId(), teamId, includeArchived);
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@AuthenticationPrincipal AppUserPrincipal principal,
                               @PathVariable UUID teamId, @PathVariable UUID projectId) {
        return projects.get(principal.getUserId(), teamId, projectId);
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal AppUserPrincipal principal,
                                                  @PathVariable UUID teamId,
                                                  @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projects.create(principal.getUserId(), teamId, request));
    }

    @PutMapping("/{projectId}")
    public ProjectResponse update(@AuthenticationPrincipal AppUserPrincipal principal,
                                  @PathVariable UUID teamId, @PathVariable UUID projectId,
                                  @Valid @RequestBody ProjectRequest request) {
        return projects.update(principal.getUserId(), teamId, projectId, request);
    }

    @PostMapping("/{projectId}/archive")
    public ProjectResponse archive(@AuthenticationPrincipal AppUserPrincipal principal,
                                   @PathVariable UUID teamId, @PathVariable UUID projectId) {
        return projects.archive(principal.getUserId(), teamId, projectId);
    }
}
