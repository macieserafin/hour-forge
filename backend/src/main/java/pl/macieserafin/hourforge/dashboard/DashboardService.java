package pl.macieserafin.hourforge.dashboard;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.user.UserRepository;

@Service
public class DashboardService {
    private final UserRepository users;
    private final TeamMemberRepository members;
    private final DashboardRepository dashboard;
    private final Clock clock;

    public DashboardService(UserRepository users, TeamMemberRepository members, DashboardRepository dashboard, Clock clock) {
        this.users = users;
        this.members = members;
        this.dashboard = dashboard;
        this.clock = clock;
    }

    public DashboardSummary summary(UUID userId, UUID teamId) {
        return summary(userId, teamId, pl.macieserafin.hourforge.preferences.UserPreferences.WeekStart.MONDAY);
    }

    /** The week starts on the day the person chose; the seven days of {@code days} follow from it. */
    @Transactional(readOnly = true)
    public DashboardOverview overview(UUID userId, pl.macieserafin.hourforge.preferences.UserPreferences.WeekStart firstDay) {
        if (!users.existsById(userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var teams = new java.util.ArrayList<DashboardOverview.TeamTotal>();
        long today = 0;
        long week = 0;
        for (var membership : members.findActiveByUserId(userId)) {
            var summary = summary(userId, membership.getTeam().getId(), firstDay);
            teams.add(new DashboardOverview.TeamTotal(membership.getTeam().getId(), membership.getTeam().getName(), membership.getTeam().isArchived(),
                    summary.todaySeconds(), summary.weekSeconds()));
            today += summary.todaySeconds();
            week += summary.weekSeconds();
        }
        teams.sort(java.util.Comparator.comparing(DashboardOverview.TeamTotal::teamName, String.CASE_INSENSITIVE_ORDER));
        return new DashboardOverview(today, week, java.util.List.copyOf(teams));
    }

    /** Whole weeks ending with the current one, with every day present (zero when nothing was recorded). */
    @Transactional(readOnly = true)
    public DashboardActivity activity(UUID userId, int weeks, pl.macieserafin.hourforge.preferences.UserPreferences.WeekStart firstDay) {
        if (!users.existsById(userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (weeks < 4 || weeks > 26) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose between 4 and 26 weeks");
        var now = clock.instant();
        // "Today" is the latest date in any of the person's teams, so nobody's current day is cut off.
        var today = members.findActiveByUserId(userId).stream().map(member -> now.atZone(ZoneId.of(member.getTeam().getTimezone())).toLocalDate())
                .max(java.util.Comparator.naturalOrder()).orElse(now.atZone(java.time.ZoneOffset.UTC).toLocalDate());
        var day = firstDay == pl.macieserafin.hourforge.preferences.UserPreferences.WeekStart.SUNDAY ? DayOfWeek.SUNDAY : DayOfWeek.MONDAY;
        var from = today.with(TemporalAdjusters.previousOrSame(day)).minusWeeks(weeks - 1L);
        var to = from.plusWeeks(weeks).minusDays(1);
        var perDay = dashboard.dailyTotalsAllTeams(userId, from, to).stream()
                .collect(java.util.stream.Collectors.toMap(DashboardDayTotal::workDate, DashboardDayTotal::seconds));
        var days = from.datesUntil(to.plusDays(1)).map(date -> new DashboardDayTotal(date, perDay.getOrDefault(date, 0L))).toList();
        long max = days.stream().mapToLong(DashboardDayTotal::seconds).max().orElse(0);
        return new DashboardActivity(from, to, today, max, days);
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(UUID userId, UUID teamId, pl.macieserafin.hourforge.preferences.UserPreferences.WeekStart firstDay) {
        if (!users.existsById(userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var membership = members.findByTeamIdAndUserId(teamId, userId)
                .filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var timezone = membership.getTeam().getTimezone();
        var today = clock.instant().atZone(ZoneId.of(timezone)).toLocalDate();
        var weekStart = today.with(TemporalAdjusters.previousOrSame(firstDay == pl.macieserafin.hourforge.preferences.UserPreferences.WeekStart.SUNDAY ? DayOfWeek.SUNDAY : DayOfWeek.MONDAY));
        var weekEnd = weekStart.plusDays(6);
        var totals = dashboard.totals(userId, teamId, today, weekStart, weekEnd);
        var perDay = dashboard.dailyTotals(userId, teamId, weekStart, weekEnd).stream()
                .collect(java.util.stream.Collectors.toMap(DashboardDayTotal::workDate, DashboardDayTotal::seconds));
        var days = weekStart.datesUntil(weekEnd.plusDays(1)).map(date -> new DashboardSummary.Day(date, perDay.getOrDefault(date, 0L))).toList();
        return new DashboardSummary(teamId, timezone, today, weekStart, weekEnd, totals.todaySeconds(), totals.weekSeconds(), days);
    }
}
