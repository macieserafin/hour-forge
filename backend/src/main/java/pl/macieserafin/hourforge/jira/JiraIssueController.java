package pl.macieserafin.hourforge.jira;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.security.RequestRateLimits;

@RestController
@RequestMapping("/api/integrations/jira")
@ConditionalOnProperty(name = "app.jira.enabled", havingValue = "true")
public class JiraIssueController {
    public record IssueResponse(String id, String key, String summary, String status, String issueType, String projectKey) {
    }

    public record IssuesResponse(List<IssueResponse> issues, String nextPageToken) {
    }

    private final JiraIssueService issues;
    private final RequestRateLimits rateLimits;

    public JiraIssueController(JiraIssueService issues, RequestRateLimits rateLimits) {
        this.issues = issues;
        this.rateLimits = rateLimits;
    }

    /** The title and state of one issue the person may see, for a hover card; nothing is stored. */
    @GetMapping("/issues/{issueId:[0-9]{1,20}}")
    public IssueResponse one(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable String issueId, @RequestParam UUID teamId) {
        rateLimits.jiraSearch(principal.getUserId());
        var issue = issues.lookup(principal.getUserId(), teamId, issueId);
        return new IssueResponse(issue.id(), issue.key(), issue.summary(), issue.status(), issue.issueType(), issue.projectKey());
    }

    @GetMapping("/issues")
    public IssuesResponse search(@AuthenticationPrincipal AppUserPrincipal principal, @RequestParam UUID teamId,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(defaultValue = "ME") JiraJql.Assignee assignee,
                                 @RequestParam(defaultValue = "ANY") JiraJql.SprintFilter sprint,
                                 @RequestParam(defaultValue = "false") boolean includeDone,
                                 @RequestParam(required = false) String project,
                                 @RequestParam(required = false) String pageToken) {
        rateLimits.jiraSearch(principal.getUserId());
        var result = issues.search(principal.getUserId(), teamId,
                new JiraJql.Query(assignee, sprint, includeDone, project, q), pageToken);
        return new IssuesResponse(result.issues().stream()
                .map(issue -> new IssueResponse(issue.id(), issue.key(), issue.summary(), issue.status(), issue.issueType(), issue.projectKey()))
                .toList(), result.nextPageToken());
    }
}
