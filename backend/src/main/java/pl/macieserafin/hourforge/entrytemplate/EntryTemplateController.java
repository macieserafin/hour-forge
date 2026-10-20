package pl.macieserafin.hourforge.entrytemplate;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.timeentry.IssueLink;
import pl.macieserafin.hourforge.timeentry.IssueLinks;

@RestController
@RequestMapping("/api/teams/{teamId}/templates")
public class EntryTemplateController {
    private final EntryTemplateService templates;
    private final IssueLinks links;

    public EntryTemplateController(EntryTemplateService templates, IssueLinks links) {
        this.templates = templates;
        this.links = links;
    }

    @GetMapping
    public List<TemplateResponse> list(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId) {
        return templates.list(principal.getUserId(), teamId);
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> create(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId,
                                                   @Valid @RequestBody TemplateRequest request) {
        var link = links.forNewEntry(principal.getUserId(), teamId, request.jiraIssueId());
        return ResponseEntity.status(HttpStatus.CREATED).body(templates.create(principal.getUserId(), teamId, request, link));
    }

    @PutMapping("/{templateId}")
    public TemplateResponse update(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId, @PathVariable UUID templateId,
                                   @Valid @RequestBody TemplateRequest request) {
        IssueLink current = templates.currentLink(principal.getUserId(), templateId);
        var link = links.keepOrResolve(principal.getUserId(), teamId, request.jiraIssueId(), current);
        return templates.update(principal.getUserId(), teamId, templateId, request, link);
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID teamId, @PathVariable UUID templateId) {
        templates.delete(principal.getUserId(), teamId, templateId);
        return ResponseEntity.noContent().build();
    }
}
