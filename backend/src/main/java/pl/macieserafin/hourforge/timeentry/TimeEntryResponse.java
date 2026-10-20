package pl.macieserafin.hourforge.timeentry;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimeEntryResponse(UUID id, UUID userId, UUID teamId, UUID projectId, EntryType entryType,
                                LocalDate workDate, Instant startedAt, Instant endedAt, Integer durationSeconds,
                                String description, Instant createdAt, Instant updatedAt,
                                Instant pausedAt, double pausedDurationSeconds, String jiraIssueId, String jiraIssueKey) {
}
