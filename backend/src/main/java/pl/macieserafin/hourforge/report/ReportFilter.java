package pl.macieserafin.hourforge.report;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import pl.macieserafin.hourforge.timeentry.EntryType;
import org.springframework.format.annotation.DateTimeFormat;

/** {@code q} matches descriptions case-insensitively; an empty value means no text filter. */
public record ReportFilter(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, List<UUID> userIds,
                           List<UUID> projectIds, EntryType entryType, Boolean undated, String q, List<UUID> tagIds, String issueKey) {
    public ReportFilter(LocalDate from, LocalDate to, List<UUID> userIds, List<UUID> projectIds, EntryType entryType, Boolean undated, String q) {
        this(from, to, userIds, projectIds, entryType, undated, q, null, null);
    }

    public ReportFilter(LocalDate from, LocalDate to, List<UUID> userIds, List<UUID> projectIds, EntryType entryType, Boolean undated, String q, List<UUID> tagIds) {
        this(from, to, userIds, projectIds, entryType, undated, q, tagIds, null);
    }

    public ReportFilter(LocalDate from, LocalDate to, List<UUID> userIds, List<UUID> projectIds, EntryType entryType) {
        this(from, to, userIds, projectIds, entryType, false, null, null, null);
    }

    public ReportFilter(LocalDate from, LocalDate to, List<UUID> userIds, List<UUID> projectIds, EntryType entryType,
                        Boolean undated) {
        this(from, to, userIds, projectIds, entryType, undated, null, null, null);
    }

    public ReportFilter {
        undated = Boolean.TRUE.equals(undated);
        userIds = userIds == null ? List.of() : List.copyOf(userIds);
        projectIds = projectIds == null ? List.of() : List.copyOf(projectIds);
        tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
        q = q == null || q.isBlank() ? null : q.strip();
        issueKey = issueKey == null || issueKey.isBlank() ? null : issueKey.strip().toUpperCase(java.util.Locale.ROOT);
    }
}
