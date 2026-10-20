package pl.macieserafin.hourforge.timeentry;

import java.util.UUID;

/** Checks with the issue tracker that the person may see the issue. Implemented only while Jira is switched on. */
public interface IssueLinkResolver {
    IssueLink resolve(UUID userId, UUID teamId, String issueId);
}
