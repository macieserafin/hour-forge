package pl.macieserafin.hourforge.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** {@code days} always holds the seven days of the week, Monday first, so a chart needs no gap filling. */
public record DashboardSummary(UUID teamId, String timezone, LocalDate today, LocalDate weekStart,
                               LocalDate weekEnd, long todaySeconds, long weekSeconds, List<Day> days) {
    public record Day(LocalDate date, long seconds) {
    }
}
