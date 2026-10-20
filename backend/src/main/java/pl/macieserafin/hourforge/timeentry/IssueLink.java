package pl.macieserafin.hourforge.timeentry;

import java.util.UUID;

/** A verified pointer from a time entry to a Jira issue: the team's connection, the issue id and its key. */
public record IssueLink(UUID connectionId, String issueId, String issueKey) {
}
