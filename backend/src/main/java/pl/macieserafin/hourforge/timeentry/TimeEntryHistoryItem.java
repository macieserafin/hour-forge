package pl.macieserafin.hourforge.timeentry;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimeEntryHistoryItem(UUID id, UUID teamId, UUID projectId, String projectName,
                                   EntryType entryType, LocalDate workDate, int durationSeconds,
                                   String description, Instant startedAt, Instant endedAt,
                                   String jiraIssueId, String jiraIssueKey) {
}
