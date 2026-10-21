package pl.macieserafin.hourforge.invitation.unit;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.invitation.CreateInviteRequest;
import pl.macieserafin.hourforge.invitation.InvitationService;
import pl.macieserafin.hourforge.invitation.TeamInvite;
import pl.macieserafin.hourforge.invitation.TeamInviteRepository;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pl.macieserafin.hourforge.support.UnitFixtures.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {
    @Mock TeamInviteRepository invites;
    @Mock TeamRepository teams;
    @Mock TeamMemberRepository members;
    @Mock UserRepository users;
    private InvitationService service;
    private final User actor = user();
    private final Team team = team();
    @BeforeEach void setup() { service = new InvitationService(invites, teams, members, users, "https://hourforge.test", CLOCK, org.mockito.Mockito.mock(pl.macieserafin.hourforge.team.TeamMailService.class)); }
    private void creator(TeamRole role) {
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(teams.findLockedById(team.getId())).thenReturn(Optional.of(team));
        when(members.findByTeamIdAndUserId(team.getId(), actor.getId())).thenReturn(Optional.of(new TeamMember(team, actor, role, NOW)));
    }
    @Test void onlyOwnerCanGenerateAnInvitation() {
        creator(TeamRole.MEMBER);
        assertThatThrownBy(() -> service.create(actor.getId(), team.getId(), new CreateInviteRequest(NOW.plusSeconds(60), 1)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
        verifyNoInteractions(invites);
    }
    @Test void expiryMustBeStrictlyAfterServerTime() {
        creator(TeamRole.OWNER);
        assertThatThrownBy(() -> service.create(actor.getId(), team.getId(), new CreateInviteRequest(NOW, 1))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(invites);
    }
    @Test void defaultsAndMaximumValidityUseTheControlledClock() {
        creator(TeamRole.OWNER);
        when(invites.saveAndFlush(any())).thenAnswer(call -> identified(call.getArgument(0)));
        var defaults = service.create(actor.getId(), team.getId(), new CreateInviteRequest(null, null));
        assertThat(defaults.expiresAt()).isEqualTo(NOW.plusSeconds(7 * 86400));
        assertThat(defaults.maxUses()).isEqualTo(1);
        var boundary = service.create(actor.getId(), team.getId(), new CreateInviteRequest(NOW.plusSeconds(30 * 86400), 20));
        assertThat(boundary.maxUses()).isEqualTo(20);
        assertThatThrownBy(() -> service.create(actor.getId(), team.getId(), new CreateInviteRequest(NOW.plusSeconds(30 * 86400 + 1), 1)))
                .isInstanceOf(ResponseStatusException.class);
    }
    @Test void generatedTokenIsRandomAndOnlyItsHashIsPersisted() {
        creator(TeamRole.OWNER);
        when(invites.saveAndFlush(any())).thenAnswer(call -> identified(call.getArgument(0)));
        var first = service.create(actor.getId(), team.getId(), new CreateInviteRequest(NOW.plusSeconds(60), 1));
        var second = service.create(actor.getId(), team.getId(), new CreateInviteRequest(NOW.plusSeconds(60), 1));
        assertThat(first.token()).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second.token());
        var saved = ArgumentCaptor.forClass(TeamInvite.class);
        verify(invites, times(2)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues().getFirst().getTokenHash()).hasSize(64).isNotEqualTo(first.token());
        assertThat(first.invitationUrl()).endsWith("/invite/" + first.token());
    }
    @ParameterizedTest @ValueSource(strings = {"", "short", "../token"})
    void malformedTokensNeverReachTheInvitationRepository(String token) {
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        assertThatThrownBy(() -> service.accept(actor.getId(), token)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(invites);
    }
    @ParameterizedTest @ValueSource(strings = {"ftp://host", "https://host?token=secret", "https://user:pass@host", "/relative"})
    void rejectsUnsafeFrontendAddresses(String url) {
        assertThatThrownBy(() -> new InvitationService(invites, teams, members, users, url, CLOCK, org.mockito.Mockito.mock(pl.macieserafin.hourforge.team.TeamMailService.class))).isInstanceOf(IllegalArgumentException.class);
    }
}
