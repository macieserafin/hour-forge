package pl.macieserafin.hourforge.jira;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.macieserafin.hourforge.timeentry.IssueLink;
import pl.macieserafin.hourforge.timeentry.IssueLinkResolver;

/** Lets the time tracking code ask Jira, as the person, whether an issue may be linked. */
@Component
@ConditionalOnProperty(name = "app.jira.enabled", havingValue = "true")
public class JiraIssueLinkResolver implements IssueLinkResolver {
    private final JiraIssueService issues;

    public JiraIssueLinkResolver(JiraIssueService issues) {
        this.issues = issues;
    }

    @Override
    public IssueLink resolve(UUID userId, UUID teamId, String issueId) {
        var found = issues.find(userId, teamId, issueId);
        return new IssueLink(found.connectionId(), found.issue().id(), found.issue().key());
    }
}
