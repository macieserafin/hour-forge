package pl.macieserafin.hourforge.dashboard;

import java.time.LocalDate;

public record DashboardDayTotal(LocalDate workDate, long seconds) {
}
