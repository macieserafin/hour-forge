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

    @Transactional(readOnly = true)
    public DashboardSummary summary(UUID userId, UUID teamId) {
        if (!users.existsById(userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var membership = members.findByTeamIdAndUserId(teamId, userId)
                .filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var timezone = membership.getTeam().getTimezone();
        var today = clock.instant().atZone(ZoneId.of(timezone)).toLocalDate();
        var weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var weekEnd = weekStart.plusDays(6);
        var totals = dashboard.totals(userId, teamId, today, weekStart, weekEnd);
        return new DashboardSummary(teamId, timezone, today, weekStart, weekEnd, totals.todaySeconds(), totals.weekSeconds());
    }
}
