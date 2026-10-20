package pl.macieserafin.hourforge.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;

public final class ReportResponse {
    private ReportResponse() {}

    public record Context(UUID teamId, String teamName, String timezone, ReportFilter filter) {}
    public record Totals(long durationSeconds, long entryCount) {}
    public record Summary(Context context, Totals totals) {}
    public record Page<T>(List<T> content, int page, int size, boolean hasNext) {}
    public record Result<T>(Context context, Page<T> data) {}
    public record Breakdown(ReportGrouping groupBy, Context context, Page<Bucket> data) {}
    public record Bucket(LocalDate workDate, UUID userId, String displayName,
                         UUID projectId, String projectName, Instant projectArchivedAt,
                         long durationSeconds, long entryCount) {}
    public record Member(UUID userId, String displayName, TeamRole role, Instant joinedAt, Instant leftAt) {}
    public record Project(UUID projectId, String name, String description, Instant createdAt, Instant archivedAt) {}
    public record Entry(UUID id, UUID userId, String displayName, UUID projectId, String projectName,
                        Instant projectArchivedAt, EntryType entryType, LocalDate workDate,
                        Instant startedAt, Instant endedAt, Integer durationSeconds, String description,
                        Instant pausedAt, long pausedDurationMicros, Instant createdAt, Instant updatedAt,
                        String jiraIssueKey, Instant editedAt) {}
    public record ActiveTimer(Entry entry, long elapsedSeconds) {}
    public record Active(Context context, Instant asOf, Page<ActiveTimer> data) {}
}
