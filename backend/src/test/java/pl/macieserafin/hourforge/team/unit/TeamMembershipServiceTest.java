package pl.macieserafin.hourforge.team.unit;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.team.*;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pl.macieserafin.hourforge.support.UnitFixtures.*;

@ExtendWith(MockitoExtension.class)
class TeamMembershipServiceTest {
    @Mock TeamRepository teams;
    @Mock TeamMemberRepository members;
    @Mock UserRepository users;
    private TeamMembershipService service;
    private Team team;
    private User actor;

    @BeforeEach void setup() {
        service = new TeamMembershipService(teams, members, users, CLOCK);
        team = team();
        actor = user();
    }

    private TeamMember membership(TeamRole role) {
        when(users.existsById(actor.getId())).thenReturn(true);
        var membership = new TeamMember(team, actor, role, NOW.minusSeconds(3600));
        when(members.findByTeamIdAndUserId(team.getId(), actor.getId())).thenReturn(Optional.of(membership));
        return membership;
    }

    @Test void leavingRecordsTheControlledClockWithoutDeletingMembershipOrHistory() {
        var membership = membership(TeamRole.MEMBER);
        when(teams.findLockedById(team.getId())).thenReturn(Optional.of(team));
        service.leave(actor.getId(), team.getId());
        assertThat(membership.getLeftAt()).isEqualTo(NOW);
        verify(members).save(membership);
        verify(members, never()).delete(any());
    }

    @Test void repeatedRemovalPreservesTheOriginalDepartureTime() {
        membership(TeamRole.OWNER);
        when(teams.findLockedById(team.getId())).thenReturn(Optional.of(team));
        var target = user();
        var former = new TeamMember(team, target, TeamRole.MEMBER, NOW.minusSeconds(3600));
        former.setLeftAt(NOW.minusSeconds(60));
        when(members.findByTeamIdAndUserId(team.getId(), target.getId())).thenReturn(Optional.of(former));
        service.remove(actor.getId(), team.getId(), target.getId());
        assertThat(former.getLeftAt()).isEqualTo(NOW.minusSeconds(60));
        verify(members, never()).save(any());
    }

    @Test void anOwnerCannotLeaveOrRemoveThemselves() {
        membership(TeamRole.OWNER);
        when(teams.findLockedById(team.getId())).thenReturn(Optional.of(team));
        for (Runnable action : List.<Runnable>of(() -> service.leave(actor.getId(), team.getId()),
                () -> service.remove(actor.getId(), team.getId(), actor.getId()))) {
            assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                    e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        }
        verify(members, never()).save(any());
    }

    @Test void deletedUsersCannotReadMemberships() {
        assertThatThrownBy(() -> service.list(actor.getId(), team.getId(), false, 0, 20))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        verifyNoInteractions(teams, members);
    }
}
