package pl.macieserafin.hourforge.timeentry.unit;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.ManualTimeEntryRequest;
import pl.macieserafin.hourforge.timeentry.StartTimerRequest;
import pl.macieserafin.hourforge.timeentry.StopTimerRequest;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.timeentry.TimeEntryService;
import pl.macieserafin.hourforge.timeentry.TimerStateRequest;
import pl.macieserafin.hourforge.timeentry.UpdateTimeEntryRequest;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pl.macieserafin.hourforge.support.UnitFixtures.*;

@ExtendWith(MockitoExtension.class)
class TimeEntryServiceTest {
    @Mock TimeEntryRepository entries;
    @Mock UserRepository users;
    @Mock TeamRepository teams;
    @Mock TeamMemberRepository members;
    @Mock ProjectRepository projects;
    private TimeEntryService service;
    private final User actor = user();
    private final Team team = team();
    private final Project project = project(team);

    @BeforeEach void setup() { service = new TimeEntryService(entries, users, teams, members, projects, CLOCK); }
    private TimeEntry timer() {
        var entry = identified(new TimeEntry(actor, team, project, EntryType.TIMER, LocalDate.of(2026, 10, 19), NOW.minusSeconds(120), null, null, "Original"));
        when(users.findLockedForTimeEntryById(actor.getId())).thenReturn(Optional.of(actor));
        when(entries.findByIdAndUserId(entry.getId(), actor.getId())).thenReturn(Optional.of(entry));
        return entry;
    }
    private void writeAccess() {
        when(users.findLockedForTimeEntryById(actor.getId())).thenReturn(Optional.of(actor));
        when(teams.findLockedById(team.getId())).thenReturn(Optional.of(team));
        when(members.findByTeamIdAndUserId(team.getId(), actor.getId())).thenReturn(Optional.of(new TeamMember(team, actor, TeamRole.MEMBER, NOW)));
        when(projects.findByIdAndTeamId(project.getId(), team.getId())).thenReturn(Optional.of(project));
    }
    @Test void startsAtServerTimeUsingTheTeamWorkDate() {
        writeAccess();
        when(entries.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var response = service.start(actor.getId(), new StartTimerRequest(team.getId(), project.getId(), "Work"));
        assertThat(response.startedAt()).isEqualTo(NOW);
        assertThat(response.workDate()).isEqualTo(NOW.atZone(ZoneId.of(team.getTimezone())).toLocalDate());
    }
    @Test void stopsWithExactWholeSecondsAndCanClearDescription() {
        var timer = timer();
        when(entries.saveAndFlush(timer)).thenReturn(timer);
        var response = service.stop(actor.getId(), new StopTimerRequest(timer.getId(), " "));
        assertThat(response.durationSeconds()).isEqualTo(120);
        assertThat(response.endedAt()).isEqualTo(NOW);
        assertThat(response.description()).isNull();
    }
    @Test void excludesAllPausedTimeWhenStopping() {
        var timer = timer();
        timer.setPausedAt(NOW.minusSeconds(30));
        timer.setPausedDurationMicros(10_000_000);
        when(entries.saveAndFlush(timer)).thenReturn(timer);
        assertThat(service.stop(actor.getId(), new StopTimerRequest(timer.getId(), null)).durationSeconds()).isEqualTo(80);
        assertThat(timer.getPausedAt()).isNull();
        assertThat(timer.getPausedDurationMicros()).isEqualTo(40_000_000);
    }
    @Test void rejectsAZeroDurationStopWithoutCompletingTheTimer() {
        var timer = timer();
        timer.setStartedAt(NOW);
        assertThatThrownBy(() -> service.stop(actor.getId(), new StopTimerRequest(timer.getId(), null))).isInstanceOf(ResponseStatusException.class);
        assertThat(timer.getEndedAt()).isNull();
    }
    @Test void pauseRecordsServerTimeWithoutCompletingTheEntry() {
        var timer = timer();
        when(entries.saveAndFlush(timer)).thenReturn(timer);
        service.pause(actor.getId(), new TimerStateRequest(timer.getId(), "Break"));
        assertThat(timer.getPausedAt()).isEqualTo(NOW);
        assertThat(timer.getEndedAt()).isNull();
        assertThat(timer.getDescription()).isEqualTo("Break");
    }
    @Test void resumeRequiresAPausedEntry() {
        var timer = timer();
        assertThatThrownBy(() -> service.resume(actor.getId(), new TimerStateRequest(timer.getId(), null))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void correctsDurationWithoutRewritingTimerTimestamps() {
        var timer = timer();
        timer.setEndedAt(NOW);
        timer.setDurationSeconds(120);
        writeAccess();
        when(entries.saveAndFlush(timer)).thenReturn(timer);
        var response = service.update(actor.getId(), timer.getId(), new UpdateTimeEntryRequest(project.getId(), LocalDate.of(2026, 10, 18), BigDecimal.valueOf(60), null));
        assertThat(response.durationSeconds()).isEqualTo(60);
        assertThat(response.startedAt()).isEqualTo(NOW.minusSeconds(120));
        assertThat(response.endedAt()).isEqualTo(NOW);
    }
    @ParameterizedTest
    @CsvSource({"2026-10-19, 86400", "2024-10-19, 60"})
    void acceptsManualEntriesAtTheDurationAndWorkDateLimits(LocalDate workDate, int seconds) {
        writeAccess();
        when(entries.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var response = service.createManual(actor.getId(), new ManualTimeEntryRequest(team.getId(), project.getId(), workDate, seconds, null));
        assertThat(response.workDate()).isEqualTo(workDate);
        assertThat(response.durationSeconds()).isEqualTo(seconds);
    }
    @ParameterizedTest
    @CsvSource({"2026-10-20, 60", "2024-10-18, 60", "2026-10-19, 86401"})
    void rejectsManualEntriesOutsideTheDurationAndWorkDateLimits(LocalDate workDate, int seconds) {
        writeAccess();
        assertThatThrownBy(() -> service.createManual(actor.getId(), new ManualTimeEntryRequest(team.getId(), project.getId(), workDate, seconds, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(entries, never()).saveAndFlush(any());
    }
    @Test void todayFollowsTheTeamTimeZone() {
        var ahead = identified(new Team("Ahead", "Pacific/Kiritimati"));
        var aheadProject = project(ahead);
        when(users.findLockedForTimeEntryById(actor.getId())).thenReturn(Optional.of(actor));
        when(teams.findLockedById(ahead.getId())).thenReturn(Optional.of(ahead));
        when(members.findByTeamIdAndUserId(ahead.getId(), actor.getId())).thenReturn(Optional.of(new TeamMember(ahead, actor, TeamRole.MEMBER, NOW)));
        when(projects.findByIdAndTeamId(aheadProject.getId(), ahead.getId())).thenReturn(Optional.of(aheadProject));
        when(entries.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        // 10:00 UTC on 2026-10-19 is already 2026-10-20 in UTC+14.
        var tomorrowInUtc = LocalDate.of(2026, 10, 20);
        assertThat(service.createManual(actor.getId(), new ManualTimeEntryRequest(ahead.getId(), aheadProject.getId(), tomorrowInUtc, 60, null))
                .workDate()).isEqualTo(tomorrowInUtc);
    }
    @Test void correctionMayKeepOldValuesOutsideTheLimitsButCannotIntroduceThem() {
        var timer = timer();
        timer.setWorkDate(LocalDate.of(2023, 1, 1));
        timer.setEndedAt(NOW);
        timer.setDurationSeconds(108_000);
        writeAccess();
        when(entries.saveAndFlush(timer)).thenReturn(timer);
        var kept = service.update(actor.getId(), timer.getId(), new UpdateTimeEntryRequest(project.getId(), LocalDate.of(2023, 1, 1), BigDecimal.valueOf(108_000), "Only text"));
        assertThat(kept.description()).isEqualTo("Only text");
        assertThatThrownBy(() -> service.update(actor.getId(), timer.getId(), new UpdateTimeEntryRequest(project.getId(), LocalDate.of(2023, 1, 1), BigDecimal.valueOf(90_000), null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.update(actor.getId(), timer.getId(), new UpdateTimeEntryRequest(project.getId(), LocalDate.of(2023, 1, 2), BigDecimal.valueOf(108_000), null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(timer.getDurationSeconds()).isEqualTo(108_000);
        assertThat(timer.getWorkDate()).isEqualTo(LocalDate.of(2023, 1, 1));
    }
    @Test void activeEntryCannotBeDeleted() {
        var timer = timer();
        when(teams.findLockedById(team.getId())).thenReturn(Optional.of(team));
        when(members.findByTeamIdAndUserId(team.getId(), actor.getId())).thenReturn(Optional.of(new TeamMember(team, actor, TeamRole.MEMBER, NOW)));
        assertThatThrownBy(() -> service.delete(actor.getId(), timer.getId())).isInstanceOf(ResponseStatusException.class);
        verify(entries, never()).delete(any(TimeEntry.class));
    }
}
