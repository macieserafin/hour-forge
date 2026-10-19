package pl.macieserafin.hourforge.dashboard;

import java.time.LocalDate;
import java.util.UUID;

public record DashboardSummary(UUID teamId, String timezone, LocalDate today, LocalDate weekStart,
                               LocalDate weekEnd, long todaySeconds, long weekSeconds) {
}
