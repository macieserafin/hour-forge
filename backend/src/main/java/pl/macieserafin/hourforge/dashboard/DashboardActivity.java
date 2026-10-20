package pl.macieserafin.hourforge.dashboard;

import java.time.LocalDate;
import java.util.List;

/** One person's time per day, whole weeks long, across every team. {@code today} lets a client grey out days to come. */
public record DashboardActivity(LocalDate from, LocalDate to, LocalDate today, long maxSeconds, List<DashboardDayTotal> days) {
}
