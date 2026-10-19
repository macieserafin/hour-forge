package pl.macieserafin.hourforge.invitation.integration;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.invitation.CreateInviteRequest;
import pl.macieserafin.hourforge.invitation.InvitationService;
import pl.macieserafin.hourforge.invitation.InviteResponse;
import pl.macieserafin.hourforge.invitation.TeamInvite;
import pl.macieserafin.hourforge.invitation.TeamInviteRepository;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = "app.frontend-url=https://hourforge.test")
class InvitationConcurrencyIT extends IntegrationTestSupport {
    @Autowired InvitationService service;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean TeamInviteRepository invites;

    private final List<UUID> userIds = new ArrayList<>();
    private User owner;
    private User first;
    private User second;
    private Team team;

    @BeforeEach
    void fixtures() {
        owner = account();
        first = account();
        second = account();
        team = fixtures.team("Concurrent invitations", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
    }

    @AfterEach
    void removeOwnedFixtures() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (team != null) {
                jdbc.update("delete from team_invites where team_id = ?", team.getId());
                jdbc.update("delete from team_members where team_id = ?", team.getId());
                jdbc.update("delete from teams where id = ?", team.getId());
            }
            for (UUID id : userIds) jdbc.update("delete from users where id = ?", id);
        });
    }

    @Test
    void twoUsersCannotExceedSingleUseLimit() throws Exception {
        var invite = create(1);
        var outcomes = concurrently(() -> acceptStatus(first.getId(), invite.token()),
                () -> acceptStatus(second.getId(), invite.token()));
        assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
        assertThat(invites.findById(invite.id()).orElseThrow().getUsedCount()).isEqualTo(1);
        assertThat(memberCount()).isEqualTo(2); // One owner and one accepted invite.
    }

    @Test
    void sameUserAcceptingTwiceCreatesOnlyOneMembershipAndUsesOnce() throws Exception {
        var invite = create(1);
        var outcomes = concurrently(() -> acceptStatus(first.getId(), invite.token()),
                () -> acceptStatus(first.getId(), invite.token()));
        assertThat(outcomes).containsExactly(200, 200);
        assertThat(invites.findById(invite.id()).orElseThrow().getUsedCount()).isEqualTo(1);
        assertThat(memberCount()).isEqualTo(2);
    }

    @Test
    void sameUserUsingDifferentInvitesStillJoinsOnlyOnce() throws Exception {
        var a = create(1);
        var b = create(1);
        var outcomes = concurrently(() -> acceptStatus(first.getId(), a.token()),
                () -> acceptStatus(first.getId(), b.token()));
        assertThat(outcomes).containsExactly(200, 200);
        int used = invites.findById(a.id()).orElseThrow().getUsedCount()
                + invites.findById(b.id()).orElseThrow().getUsedCount();
        assertThat(used).isEqualTo(1);
        assertThat(memberCount()).isEqualTo(2);
    }

    @Test
    void membershipAndUsageAreRolledBackWhenInvitationUpdateFails() {
        var invite = create(1);
        doThrow(new DataIntegrityViolationException("Simulated invitation update failure"))
                .when(invites).saveAndFlush(any(TeamInvite.class));
        assertThatThrownBy(() -> service.accept(first.getId(), invite.token()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(members.findByTeamIdAndUserId(team.getId(), first.getId())).isEmpty();
        assertThat(invites.findById(invite.id()).orElseThrow().getUsedCount()).isZero();
        assertThat(memberCount()).isEqualTo(1);
    }

    private List<Integer> concurrently(Callable<Integer> a, Callable<Integer> b) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> { ready.countDown(); start.await(); return a.call(); });
            var secondResult = executor.submit(() -> { ready.countDown(); start.await(); return b.call(); });
            boolean bothReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertThat(bothReady).isTrue();
            return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        }
    }

    private int acceptStatus(UUID userId, String token) {
        try {
            service.accept(userId, token);
            return 200;
        } catch (ResponseStatusException exception) {
            return exception.getStatusCode().value();
        }
    }

    private User account() {
        var user = fixtures.user(UUID.randomUUID() + "@invite-test.local", "Invitation Test");
        userIds.add(user.getId());
        return user;
    }

    private InviteResponse create(Integer maxUses) {
        return service.create(owner.getId(), team.getId(), new CreateInviteRequest(Instant.now().plusSeconds(3600), maxUses));
    }

    private int memberCount() {
        return jdbc.queryForObject("select count(*) from team_members where team_id = ?", Integer.class, team.getId());
    }
}
