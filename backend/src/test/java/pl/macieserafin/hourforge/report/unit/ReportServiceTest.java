package pl.macieserafin.hourforge.report.unit;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.report.*;
import pl.macieserafin.hourforge.team.*;
import pl.macieserafin.hourforge.timeentry.*;
import pl.macieserafin.hourforge.user.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pl.macieserafin.hourforge.support.UnitFixtures.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock UserRepository users;
    @Mock TeamMemberRepository members;
    @Mock ReportRepository reports;
    private final ReportFilter empty = new ReportFilter(null, null, null, null, null);

    private ReportService ownerService(Team team, Clock clock) {
        var actor = user();
        when(users.existsById(actor.getId())).thenReturn(true);
        when(members.findByTeamIdAndUserId(team.getId(), actor.getId()))
                .thenReturn(Optional.of(new TeamMember(team, actor, TeamRole.OWNER, NOW)));
        actorId = actor.getId();
        return new ReportService(users, members, reports, clock);
    }

    private UUID actorId;

    @ParameterizedTest
    @CsvSource({
        "2026-01-04T23:30:00Z, Pacific/Kiritimati, 2026-01-05",
        "2026-01-04T23:30:00Z, America/Los_Angeles, 2025-12-29",
        "2026-03-29T00:30:00Z, Europe/Warsaw, 2026-03-23"
    })
    void defaultsToTheWeekInTeamTimezone(String instant, String zone, LocalDate monday) {
        var team = identified(new Team("Team", zone));
        var service = ownerService(team, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        when(reports.containsMembers(team.getId(), List.of())).thenReturn(true);
        when(reports.containsProjects(team.getId(), List.of())).thenReturn(true);
        when(reports.containsTags(team.getId(), List.of())).thenReturn(true);
        var filter = new ReportFilter(monday, monday.plusDays(6), null, null, null);
        when(reports.totals(team.getId(), filter)).thenReturn(new ReportResponse.Totals(0, 0));
        var result = service.summary(actorId, team.getId(), empty);
        assertThat(result.context().filter()).isEqualTo(filter);
        assertThat(result.context().timezone()).isEqualTo(zone);
    }

    @Test
    void refusesDeletedActorsBeforeReadingTeamOrReportData() {
        var service = new ReportService(users, members, reports, CLOCK);
        assertThatThrownBy(() -> service.summary(UUID.randomUUID(), UUID.randomUUID(), empty))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        verifyNoInteractions(members, reports);
    }

    @Test
    void rejectsInvalidRangesBeforeReadingReports() {
        var team = team();
        var service = ownerService(team, CLOCK);
        var date = LocalDate.parse("2026-10-19");
        for (var filter : List.of(new ReportFilter(date, null, null, null, null),
                new ReportFilter(null, date, null, null, null),
                new ReportFilter(date, date.minusDays(1), null, null, null),
                new ReportFilter(null, null, Collections.nCopies(101, UUID.randomUUID()), null, null))) {
            assertThatThrownBy(() -> service.summary(actorId, team.getId(), filter))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        }
        verifyNoInteractions(reports);
    }

    @Test
    void activeTimersHaveASharedSnapshotTimeAndExcludePausesWithoutADefaultDateWindow() {
        var team = team();
        var service = ownerService(team, CLOCK);
        when(reports.containsMembers(team.getId(), List.of())).thenReturn(true);
        when(reports.containsProjects(team.getId(), List.of())).thenReturn(true);
        when(reports.containsTags(team.getId(), List.of())).thenReturn(true);
        var project = project(team);
        var running = new TimeEntry(user(), team, project, EntryType.TIMER, LocalDate.parse("2026-10-01"), NOW.minusSeconds(120), null, null, null);
        running.setPausedDurationMicros(30_500_000);
        var paused = new TimeEntry(user(), team, project, EntryType.TIMER, LocalDate.parse("2026-10-01"), NOW.minusSeconds(300), null, null, "Work");
        paused.setPausedAt(NOW.minusSeconds(60));
        paused.setPausedDurationMicros(20_000_000);
        when(reports.entries(team.getId(), empty, true, ReportEntrySort.CREATED_AT, true, 0, 20)).thenReturn(List.of(running, paused));
        var result = service.active(actorId, team.getId(), empty, 0, 20);
        assertThat(result.asOf()).isEqualTo(NOW);
        assertThat(result.context().filter().from()).isNull();
        assertThat(result.data().content()).extracting(ReportResponse.ActiveTimer::elapsedSeconds).containsExactly(89L, 220L);
    }
}
