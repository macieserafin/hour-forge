package pl.macieserafin.hourforge.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import pl.macieserafin.hourforge.timeentry.TimeEntry;

public interface DashboardRepository extends Repository<TimeEntry, UUID> {
    @Query("""
            select new pl.macieserafin.hourforge.dashboard.DashboardTotals(
                coalesce(sum(case when entry.workDate = :today then entry.durationSeconds else 0 end), 0L),
                coalesce(sum(entry.durationSeconds), 0L))
            from TimeEntry entry
            where entry.user.id = :userId and entry.team.id = :teamId
                and entry.workDate between :weekStart and :weekEnd
                and entry.durationSeconds is not null
            """)
    DashboardTotals totals(UUID userId, UUID teamId, LocalDate today, LocalDate weekStart, LocalDate weekEnd);

    @Query("""
            select new pl.macieserafin.hourforge.dashboard.DashboardDayTotal(entry.workDate, coalesce(sum(entry.durationSeconds), 0L))
            from TimeEntry entry
            where entry.user.id = :userId and entry.team.id = :teamId
                and entry.workDate between :weekStart and :weekEnd
                and entry.durationSeconds is not null
            group by entry.workDate
            """)
    List<DashboardDayTotal> dailyTotals(UUID userId, UUID teamId, LocalDate weekStart, LocalDate weekEnd);
}
