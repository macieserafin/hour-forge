package pl.macieserafin.hourforge.team.unit;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.team.CreateTeamRequest;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.team.TeamService;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pl.macieserafin.hourforge.support.UnitFixtures.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {
    @Mock TeamRepository teams;
    @Mock TeamMemberRepository members;
    @Mock UserRepository users;
    @InjectMocks TeamService service;

    @Test void createsAnOwnerMembershipForTheRequestingUser() {
        var user = user();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(teams.saveAndFlush(any())).thenAnswer(call -> identified(call.getArgument(0)));
        var response = service.createTeam(user.getId(), new CreateTeamRequest("  Studio  ", "UTC"));
        var saved = ArgumentCaptor.forClass(TeamMember.class);
        verify(members).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getRole()).isEqualTo(TeamRole.OWNER);
        assertThat(response.name()).isEqualTo("Studio");
    }
    @Test void rejectsAnUnknownCreatorWithoutWriting() {
        assertThatThrownBy(() -> service.createTeam(UUID.randomUUID(), new CreateTeamRequest("Studio", "UTC")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        verifyNoInteractions(teams, members);
    }
    @Test void listsActiveTeamsForTheAuthenticatedUser() {
        var id = UUID.randomUUID();
        when(users.existsById(id)).thenReturn(true);
        when(members.findActiveTeamsByUserId(id)).thenReturn(List.of());
        assertThat(service.getTeams(id)).isEmpty();
        verify(members).findActiveTeamsByUserId(id);
    }
    @Test void rejectsADeletedUsersSession() {
        assertThatThrownBy(() -> service.getTeams(UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(members);
    }
}
