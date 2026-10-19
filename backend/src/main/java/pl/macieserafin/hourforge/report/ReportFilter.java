package pl.macieserafin.hourforge.report;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import pl.macieserafin.hourforge.timeentry.EntryType;
import org.springframework.format.annotation.DateTimeFormat;

public record ReportFilter(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, List<UUID> userIds,
                           List<UUID> projectIds, EntryType entryType, Boolean undated) {
    public ReportFilter(LocalDate from, LocalDate to, List<UUID> userIds, List<UUID> projectIds, EntryType entryType) {
        this(from, to, userIds, projectIds, entryType, false);
    }

    public ReportFilter {
        undated = Boolean.TRUE.equals(undated);
        userIds = userIds == null ? List.of() : List.copyOf(userIds);
        projectIds = projectIds == null ? List.of() : List.copyOf(projectIds);
    }
}
