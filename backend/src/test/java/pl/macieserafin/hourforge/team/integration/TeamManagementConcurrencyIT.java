package pl.macieserafin.hourforge.team.integration;

import java.time.Instant;
import java.util.ArrayList;
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
import pl.macieserafin.hourforge.support.IntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamManagementService;
import pl.macieserafin.hourforge.team.TeamMembershipService;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.StartTimerRequest;
import pl.macieserafin.hourforge.timeentry.TimeEntryService;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;

class TeamManagementConcurrencyIT extends IntegrationTestSupport {
    @Autowired TeamManagementService management;
    @Autowired TeamMembershipService memberships;
    @Autowired TimeEntryService timeEntries;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private final List<UUID> userIds = new ArrayList<>();
    private User owner;
    private User member;
    private Team team;
    private Project project;

    @BeforeEach
    void fixtures() {
        owner = account();
        member = account();
        team = fixtures.team("Concurrent management", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
        project = fixtures.project(team, "Concurrent project", null);
    }

    @AfterEach
    void removeOwnedFixtures() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("delete from time_entries where team_id = ?", team.getId());
            jdbc.update("delete from projects where team_id = ?", team.getId());
            jdbc.update("delete from team_members where team_id = ?", team.getId());
            jdbc.update("delete from teams where id = ?", team.getId());
            for (UUID id : userIds) {
                jdbc.update("delete from user_preferences where user_id = ?", id);
                jdbc.update("delete from users where id = ?", id);
            }
        });
    }

    @Test
    void transferAndRemovalOfTheSameMemberLeaveExactlyOneOwner() throws Exception {
        var outcomes = concurrently(() -> status(() -> management.transferOwnership(owner.getId(), team.getId(), member.getId())),
                () -> status(() -> memberships.remove(owner.getId(), team.getId(), member.getId())));
        // Serialized by the team lock: either the member became owner (removal is then forbidden) or left first.
        assertThat(outcomes).isIn(List.of(200, 403), List.of(404, 200));
        assertThat(jdbc.queryForObject("select count(*) from team_members where team_id = ? and role = 'OWNER' and left_at is null",
                Integer.class, team.getId())).isEqualTo(1);
    }

    @Test
    void deletionAndTimerStartNeverLeaveAnOrphanedTimer() throws Exception {
        var outcomes = concurrently(() -> status(() -> management.delete(owner.getId(), team.getId())),
                () -> status(() -> timeEntries.start(member.getId(), new StartTimerRequest(team.getId(), project.getId(), null))));
        assertThat(outcomes).isIn(List.of(200, 404), List.of(409, 200));
        int teamsLeft = jdbc.queryForObject("select count(*) from teams where id = ?", Integer.class, team.getId());
        int timers = jdbc.queryForObject("select count(*) from time_entries where team_id = ?", Integer.class, team.getId());
        // Either the team is gone together with every entry, or it stays and keeps the started timer.
        assertThat(timers).isEqualTo(teamsLeft == 0 ? 0 : 1);
    }

    @Test
    void summaryRunsInItsOwnReadOnlyTransaction() {
        // Without a surrounding test transaction: a row lock here would fail in PostgreSQL read-only mode.
        var summary = management.summary(owner.getId(), team.getId());
        assertThat(summary.activeMembers()).isEqualTo(2);
        assertThat(summary.projects()).isEqualTo(1);
        assertThat(summary.activeTimers()).isFalse();
    }

    private List<Integer> concurrently(Callable<Integer> a, Callable<Integer> b) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { ready.countDown(); start.await(); return a.call(); });
            var second = executor.submit(() -> { ready.countDown(); start.await(); return b.call(); });
            boolean bothReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertThat(bothReady).isTrue();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    private static int status(Runnable operation) {
        try {
            operation.run();
            return 200;
        } catch (ResponseStatusException exception) {
            return exception.getStatusCode().value();
        }
    }

    private User account() {
        var user = fixtures.user(UUID.randomUUID() + "@management-test.local", "Management Test");
        userIds.add(user.getId());
        return user;
    }
}
