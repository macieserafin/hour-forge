package pl.macieserafin.hourforge.timeentry.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.project.ProjectService;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
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
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

class TimeEntryConcurrencyIT extends IntegrationTestSupport {
    @Autowired TimeEntryService service;
    @Autowired ProjectService projectService;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired ProjectRepository projects;
    @Autowired TimeEntryRepository entries;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    private User first;
    private User second;
    private Team team;
    private Team otherTeam;
    private Project project;
    private Project otherProject;

    @BeforeEach
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            first = fixtures.user(UUID.randomUUID() + "@timer.test", "First");
            second = fixtures.user(UUID.randomUUID() + "@timer.test", "Second");
            team = fixtures.team("Timer concurrency", "UTC");
            otherTeam = fixtures.team("Second timer team", "UTC");
            fixtures.member(team, first, TeamRole.OWNER, Instant.now());
            fixtures.member(team, second, TeamRole.MEMBER, Instant.now());
            fixtures.member(otherTeam, first, TeamRole.OWNER, Instant.now());
            project = fixtures.project(team, "First", null);
            otherProject = fixtures.project(otherTeam, "Second", null);
        });
    }

    @AfterEach
    void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("delete from time_entries where team_id in (?, ?)", team.getId(), otherTeam.getId());
            jdbc.update("delete from projects where team_id in (?, ?)", team.getId(), otherTeam.getId());
            jdbc.update("delete from team_members where team_id in (?, ?)", team.getId(), otherTeam.getId());
            jdbc.update("delete from teams where id in (?, ?)", team.getId(), otherTeam.getId());
            jdbc.update("delete from users where id in (?, ?)", first.getId(), second.getId());
        });
    }

    @Test
    void concurrentStartsAcrossTeamsProduceOneTimerAndOneConflict() throws Exception {
        assertThat(concurrently(() -> start(first, project), () -> start(first, otherProject)))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("select count(*) from time_entries where user_id = ? and entry_type = 'TIMER' and ended_at is null",
                Long.class, first.getId())).isEqualTo(1L);
    }

    @Test
    void differentUsersCanStartTimersInTheSameProject() throws Exception {
        assertThat(concurrently(() -> start(first, project), () -> start(second, project)))
                .containsExactlyInAnyOrder(201, 201);
    }

    @Test
    void concurrentStopsFinishExactlyOnce() throws Exception {
        var timer = new TransactionTemplate(transactionManager).execute(status -> entries.saveAndFlush(
                new TimeEntry(first, team, project, EntryType.TIMER, LocalDate.now(), Instant.now().minusSeconds(10), null, null, null)));
        var request = new StopTimerRequest(timer.getId(), "Done");
        assertThat(concurrently(() -> outcome(() -> service.stop(first.getId(), request), 200),
                () -> outcome(() -> service.stop(first.getId(), request), 200)))
                .containsExactlyInAnyOrder(200, 409);
        var stored = entries.findById(timer.getId()).orElseThrow();
        assertThat(stored.getEndedAt()).isNotNull();
        assertThat(stored.getDurationSeconds()).isPositive();
    }

    @Test
    void concurrentStartAndArchiveCannotLeaveTimerOnArchivedProject() throws Exception {
        var outcomes = concurrently(() -> start(first, project),
                () -> outcome(() -> projectService.archive(first.getId(), team.getId(), project.getId()), 200));
        assertThat(List.of(List.of(201, 409), List.of(409, 200))).contains(outcomes);
        var archived = projects.findById(project.getId()).orElseThrow().getArchivedAt() != null;
        var running = entries.existsByProjectIdAndEntryTypeAndEndedAtIsNull(project.getId(), EntryType.TIMER);
        assertThat(archived && running).isFalse();
    }

    @Test
    void manualWriteAndArchivePreserveHistoryWithoutWritingAfterArchive() throws Exception {
        var outcomes = concurrently(() -> outcome(() -> service.createManual(first.getId(),
                        new ManualTimeEntryRequest(team.getId(), project.getId(), LocalDate.now(), 60, null)), 201),
                () -> outcome(() -> projectService.archive(first.getId(), team.getId(), project.getId()), 200));
        assertThat(List.of(List.of(201, 200), List.of(409, 200))).contains(outcomes);
        assertThat(projects.findById(project.getId()).orElseThrow().getArchivedAt()).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from time_entries where project_id = ?", Long.class, project.getId()))
                .isEqualTo(outcomes.getFirst() == 201 ? 1L : 0L);
    }

    private int start(User user, Project project) throws Exception {
        return outcome(() -> service.start(user.getId(), new StartTimerRequest(project.getTeam().getId(), project.getId(), null)), 201);
    }

    @Test
    void concurrentPausesAndResumesApplyEachTransitionOnlyOnce() throws Exception {
        var timer = service.start(first.getId(), new StartTimerRequest(team.getId(), project.getId(), null));
        var request = new TimerStateRequest(timer.id(), null);
        assertThat(concurrently(() -> outcome(() -> service.pause(first.getId(), request), 200),
                () -> outcome(() -> service.pause(first.getId(), request), 200)))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(concurrently(() -> outcome(() -> service.resume(first.getId(), request), 200),
                () -> outcome(() -> service.resume(first.getId(), request), 200)))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(service.active(first.getId()).orElseThrow().pausedAt()).isNull();
    }

    @Test
    void finishingAndPausingConcurrentlyNeverLeaveAFinishedTimerPaused() throws Exception {
        var timer = new TransactionTemplate(transactionManager).execute(status -> entries.saveAndFlush(
                new TimeEntry(first, team, project, EntryType.TIMER, LocalDate.now(), Instant.now().minusSeconds(10), null, null, null)));
        var outcomes = concurrently(() -> outcome(() -> service.pause(first.getId(), new TimerStateRequest(timer.getId(), null)), 200),
                () -> outcome(() -> service.stop(first.getId(), new StopTimerRequest(timer.getId(), null)), 200));
        assertThat(List.of(List.of(200, 200), List.of(409, 200))).contains(outcomes);
        var finished = entries.findById(timer.getId()).orElseThrow();
        assertThat(finished.getEndedAt()).isNotNull();
        assertThat(finished.getPausedAt()).isNull();
        assertThat(finished.getDurationSeconds()).isPositive();
    }

    private int outcome(Callable<?> action, int success) throws Exception {
        try { action.call(); return success; }
        catch (ResponseStatusException exception) { return exception.getStatusCode().value(); }
    }

    private List<Integer> concurrently(Callable<Integer> a, Callable<Integer> b) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(a, b).stream().map(action -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return action.call();
            })).toList();
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Workers timeout");
            start.countDown();
            return List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
        }
    }
}
