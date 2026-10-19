package pl.macieserafin.hourforge.report;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.user.UserRepository;

@Service
@Transactional(readOnly = true)
public class ReportService {
    private static final int MAX_REPORT_DAYS = 366;

    private final UserRepository users;
    private final TeamMemberRepository members;
    private final ReportRepository reports;
    private final Clock clock;

    public ReportService(UserRepository users, TeamMemberRepository members, ReportRepository reports, Clock clock) {
        this.users = users;
        this.members = members;
        this.reports = reports;
        this.clock = clock;
    }

    public ReportResponse.Summary summary(UUID actorId, UUID teamId, ReportFilter filter) {
        var context = context(owner(actorId, teamId), filter, true);
        return new ReportResponse.Summary(context, reports.totals(teamId, context.filter()));
    }

    public ReportResponse.Breakdown breakdown(UUID actorId, UUID teamId, ReportFilter filter,
            ReportGrouping grouping, int page, int size) {
        var context = context(owner(actorId, teamId), filter, true);
        pagination(page, size);
        if (grouping == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grouping is required");
        return new ReportResponse.Breakdown(grouping, context, reports.breakdown(teamId, context.filter(), grouping, page, size));
    }

    public ReportResponse.Result<ReportResponse.Entry> entries(UUID actorId, UUID teamId, ReportFilter filter,
            ReportEntrySort sort, boolean ascending, int page, int size) {
        var context = context(owner(actorId, teamId), filter, true);
        pagination(page, size);
        if (sort == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sort is required");
        var rows = reports.entries(teamId, context.filter(), false, sort, ascending, page, size)
                .stream().map(this::entry).toList();
        return new ReportResponse.Result<>(context, ReportRepository.page(rows, page, size));
    }

    public ReportResponse.Active active(UUID actorId, UUID teamId, ReportFilter filter, int page, int size) {
        var context = context(owner(actorId, teamId), filter, false);
        pagination(page, size);
        Instant now = clock.instant();
        var rows = reports.entries(teamId, context.filter(), true, ReportEntrySort.CREATED_AT, true, page, size)
                .stream().map(e -> new ReportResponse.ActiveTimer(entry(e), elapsed(e, now))).toList();
        return new ReportResponse.Active(context, now, ReportRepository.page(rows, page, size));
    }

    public ReportResponse.Result<ReportResponse.Member> members(UUID actorId, UUID teamId, int page, int size) {
        var team = owner(actorId, teamId);
        pagination(page, size);
        var rows = reports.members(teamId, page, size).stream().map(m -> new ReportResponse.Member(
                m.getUser().getId(), m.getUser().getDisplayName(), m.getRole(), m.getJoinedAt(), m.getLeftAt())).toList();
        return new ReportResponse.Result<>(context(team), ReportRepository.page(rows, page, size));
    }

    public ReportResponse.Result<ReportResponse.Project> projects(UUID actorId, UUID teamId, int page, int size) {
        var team = owner(actorId, teamId);
        pagination(page, size);
        var rows = reports.projects(teamId, page, size).stream().map(p -> new ReportResponse.Project(
                p.getId(), p.getName(), p.getDescription(), p.getCreatedAt(), p.getArchivedAt())).toList();
        return new ReportResponse.Result<>(context(team), ReportRepository.page(rows, page, size));
    }

    private Team owner(UUID actorId, UUID teamId) {
        if (!users.existsById(actorId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var membership = members.findByTeamIdAndUserId(teamId, actorId).filter(m -> m.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (membership.getRole() != TeamRole.OWNER) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return membership.getTeam();
    }

    private ReportResponse.Context context(Team team) {
        return new ReportResponse.Context(team.getId(), team.getName(), team.getTimezone(), null);
    }

    private ReportResponse.Context context(Team team, ReportFilter input, boolean defaultWeek) {
        var from = input.from();
        var to = input.to();
        if (input.undated() && (from != null || to != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Undated reports cannot have a date range");
        }
        if ((from == null) != (to == null) || (from != null && (from.isAfter(to)
                || from.getYear() < 1 || to.getYear() > 9999))
                || input.userIds().size() > 100 || input.projectIds().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid from/to pair and at most 100 IDs per filter");
        }
        if (from != null && ChronoUnit.DAYS.between(from, to) + 1 > MAX_REPORT_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A report can cover at most " + MAX_REPORT_DAYS + " days");
        }
        if (!reports.containsMembers(team.getId(), input.userIds()) || !reports.containsProjects(team.getId(), input.projectIds())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (from == null && defaultWeek && !input.undated()) {
            from = clock.instant().atZone(ZoneId.of(team.getTimezone())).toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            to = from.plusDays(6);
        }
        var filter = new ReportFilter(from, to, input.userIds().stream().distinct().sorted().toList(),
                input.projectIds().stream().distinct().sorted().toList(), input.entryType(), input.undated());
        return new ReportResponse.Context(team.getId(), team.getName(), team.getTimezone(), filter);
    }

    private void pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
    }

    private ReportResponse.Entry entry(TimeEntry e) {
        return new ReportResponse.Entry(e.getId(), e.getUser().getId(), e.getUser().getDisplayName(),
                e.getProject().getId(), e.getProject().getName(), e.getProject().getArchivedAt(),
                e.getEntryType(), e.getWorkDate(), e.getStartedAt(), e.getEndedAt(), e.getDurationSeconds(),
                e.getDescription(), e.getPausedAt(), e.getPausedDurationMicros(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private long elapsed(TimeEntry entry, Instant now) {
        var end = entry.getPausedAt() == null ? now : entry.getPausedAt();
        return Math.max(0, Duration.between(entry.getStartedAt(), end)
                .minus(entry.getPausedDurationMicros(), java.time.temporal.ChronoUnit.MICROS).getSeconds());
    }
}
