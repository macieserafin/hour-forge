package pl.macieserafin.hourforge.timeentry;

import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns the issue id a client sends into a verified link before the entry is written, so Jira is never called while
 * a database transaction and its locks are held. Without the Jira integration only "no link" is accepted.
 */
@Component
public class IssueLinks {
    private final ObjectProvider<IssueLinkResolver> resolver;
    private final TimeEntryService entries;

    public IssueLinks(ObjectProvider<IssueLinkResolver> resolver, TimeEntryService entries) {
        this.resolver = resolver;
        this.entries = entries;
    }

    public IssueLink forNewEntry(UUID userId, UUID teamId, String issueId) {
        if (issueId == null) return null;
        var available = resolver.getIfAvailable();
        if (available == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "The Jira integration is switched off");
        return available.resolve(userId, teamId, issueId);
    }

    /** Keeps the existing link when the same issue is sent again, so editing a note never needs Jira. */
    public IssueLink forUpdate(UUID userId, UUID entryId, String issueId) {
        if (issueId == null) return null;
        var context = entries.linkContext(userId, entryId);
        if (context.current() != null && context.current().issueId().equals(issueId)) return context.current();
        return forNewEntry(userId, context.teamId(), issueId);
    }
}
