package pl.macieserafin.hourforge.twofactor.unit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.security.DatabaseUserDetailsService;
import pl.macieserafin.hourforge.session.LoginEvent;
import pl.macieserafin.hourforge.session.SignInCompletion;
import pl.macieserafin.hourforge.support.UnitFixtures;
import pl.macieserafin.hourforge.twofactor.TwoFactorService;
import pl.macieserafin.hourforge.twofactor.TwoFactorSignIn;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TwoFactorSignInTest {
    private static final Instant START = Instant.parse("2026-10-23T10:00:00Z");

    private final TwoFactorService twoFactor = mock(TwoFactorService.class);
    private final SignInCompletion completion = mock(SignInCompletion.class);

    private final UserCredentialRepository credentials = mock(UserCredentialRepository.class);
    private final DatabaseUserDetailsService userDetails = mock(DatabaseUserDetailsService.class);
    private final UserRepository users = mock(UserRepository.class);

    private TwoFactorSignIn at(Instant now) {
        return new TwoFactorSignIn(twoFactor, userDetails, users, credentials,
                completion, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void theCodeIsAwaitedForFiveMinutesOnly() {
        var request = new MockHttpServletRequest();
        at(START).begin(request, UUID.randomUUID(), false, 1);

        var result = at(START.plusSeconds(5 * 60 + 1)).answer(request, new MockHttpServletResponse(), "123456");

        assertThat(result).isEqualTo(TwoFactorSignIn.Result.START_AGAIN);
        verify(twoFactor, never()).verifySignIn(any(), any());
        // The waiting state is gone, so a later answer cannot revive it.
        assertThat(at(START).answer(request, new MockHttpServletResponse(), "123456")).isEqualTo(TwoFactorSignIn.Result.START_AGAIN);
    }

    @Test
    void withoutAPasswordStepThereIsNothingToAnswer() {
        assertThat(at(START).answer(new MockHttpServletRequest(), new MockHttpServletResponse(), "123456"))
                .isEqualTo(TwoFactorSignIn.Result.START_AGAIN);
        verifyNoInteractions(twoFactor, completion);
    }

    @Test
    void beginningForgetsTheSignInThePasswordStepMade() {
        var request = new MockHttpServletRequest();
        request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", "signed in");
        at(START).begin(request, UUID.randomUUID(), true, 1);
        assertThat(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
    }

    @Test
    void aCodeIsNotCheckedWhenTheCredentialsChangedSinceThePasswordStep() {
        var userId = UUID.randomUUID();
        var request = new MockHttpServletRequest();
        at(START).begin(request, userId, false, 1);
        when(credentials.findSessionVersion(userId)).thenReturn(Optional.of(2));

        var result = at(START).answer(request, new MockHttpServletResponse(), "123456");

        assertThat(result).isEqualTo(TwoFactorSignIn.Result.START_AGAIN);
        verify(twoFactor, never()).verifySignIn(any(), any());
        verifyNoInteractions(completion);
    }

    @Test
    void aPasswordStepWithoutAKnownCredentialVersionCannotBeAnswered() {
        var request = new MockHttpServletRequest();
        at(START).begin(request, UUID.randomUUID(), false, null);

        assertThat(at(START).answer(request, new MockHttpServletResponse(), "123456")).isEqualTo(TwoFactorSignIn.Result.START_AGAIN);
        verify(twoFactor, never()).verifySignIn(any(), any());
    }

    @Test
    void aCredentialChangeDuringCodeVerificationCannotFinishTheSignIn() {
        var user = UnitFixtures.user();
        var request = new MockHttpServletRequest();
        at(START).begin(request, user.getId(), false, 1);
        when(credentials.findSessionVersion(user.getId())).thenReturn(Optional.of(1));
        when(twoFactor.verifySignIn(user.getId(), "123456")).thenReturn(true);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(userDetails.loadUserByUsername(user.getEmail()))
                .thenReturn(new AppUserPrincipal(user.getId(), user.getEmail(), "hash", 2));

        assertThat(at(START).answer(request, new MockHttpServletResponse(), "123456"))
                .isEqualTo(TwoFactorSignIn.Result.START_AGAIN);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        verifyNoInteractions(completion);
    }

    @Test
    void anAddressChangedBetweenAccountReadsRequiresStartingAgain() {
        var user = UnitFixtures.user();
        var request = new MockHttpServletRequest();
        at(START).begin(request, user.getId(), false, 1);
        when(credentials.findSessionVersion(user.getId())).thenReturn(Optional.of(1));
        when(twoFactor.verifySignIn(user.getId(), "123456")).thenReturn(true);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(userDetails.loadUserByUsername(user.getEmail())).thenThrow(new UsernameNotFoundException("Invalid credentials"));

        assertThat(at(START).answer(request, new MockHttpServletResponse(), "123456"))
                .isEqualTo(TwoFactorSignIn.Result.START_AGAIN);
        assertThat(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        verifyNoInteractions(completion);
    }

    @Test
    void reassigningTheAddressDuringCodeVerificationCannotSignInAsAnotherAccount() {
        var user = UnitFixtures.user();
        var request = new MockHttpServletRequest();
        at(START).begin(request, user.getId(), false, 1);
        when(credentials.findSessionVersion(user.getId())).thenReturn(Optional.of(1));
        when(twoFactor.verifySignIn(user.getId(), "123456")).thenReturn(true);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(userDetails.loadUserByUsername(user.getEmail()))
                .thenReturn(new AppUserPrincipal(UUID.randomUUID(), user.getEmail(), "hash", 1));

        try {
            assertThat(at(START).answer(request, new MockHttpServletResponse(), "123456"))
                    .isEqualTo(TwoFactorSignIn.Result.START_AGAIN);
            assertThat(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
            verifyNoInteractions(completion);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void aWrongCodeAfterGoogleIsRecordedAsAGoogleSignIn() {
        var userId = UUID.randomUUID();
        var request = new MockHttpServletRequest();
        at(START).begin(request, userId, false, 1, LoginEvent.Method.GOOGLE);
        when(credentials.findSessionVersion(userId)).thenReturn(Optional.of(1));
        when(twoFactor.verifySignIn(userId, "123456")).thenReturn(false);

        assertThat(at(START).answer(request, new MockHttpServletResponse(), "123456")).isEqualTo(TwoFactorSignIn.Result.WRONG_CODE);
        verify(completion).failSecondStep(request, userId, LoginEvent.Method.GOOGLE);
    }

    @Test
    void theRightCodeAfterGoogleFinishesAGoogleSignIn() {
        var user = UnitFixtures.user();
        var request = new MockHttpServletRequest();
        at(START).begin(request, user.getId(), true, 1, LoginEvent.Method.GOOGLE);
        when(credentials.findSessionVersion(user.getId())).thenReturn(Optional.of(1));
        when(twoFactor.verifySignIn(user.getId(), "123456")).thenReturn(true);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(userDetails.loadUserByUsername(user.getEmail())).thenReturn(new AppUserPrincipal(user.getId(), user.getEmail(), "hash", 1));

        try {
            assertThat(at(START).answer(request, new MockHttpServletResponse(), "123456")).isEqualTo(TwoFactorSignIn.Result.SIGNED_IN);
            verify(completion).complete(request, user.getId(), true, LoginEvent.Method.GOOGLE);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void aPasswordStepIsStillRecordedAsAPasswordSignIn() {
        var userId = UUID.randomUUID();
        var request = new MockHttpServletRequest();
        at(START).begin(request, userId, false, 1);
        when(credentials.findSessionVersion(userId)).thenReturn(Optional.of(1));
        when(twoFactor.verifySignIn(userId, "123456")).thenReturn(false);

        at(START).answer(request, new MockHttpServletResponse(), "123456");
        verify(completion).failSecondStep(request, userId, LoginEvent.Method.PASSWORD);
    }

    @Test
    void aSessionStoredBeforeTheMethodWasKeptIsAPasswordSignIn() throws Exception {
        // A Pending read from a session saved by the previous version has no method: records fill it with null.
        var userId = UUID.randomUUID();
        var request = new MockHttpServletRequest();
        at(START).begin(request, userId, false, 1);
        var pendingType = Class.forName("pl.macieserafin.hourforge.twofactor.TwoFactorSignIn$Pending");
        var constructor = pendingType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        request.getSession().setAttribute(TwoFactorSignIn.class.getName() + ".PENDING",
                constructor.newInstance(userId, false, START.plusSeconds(300), 0, null));
        when(credentials.findSessionVersion(userId)).thenReturn(Optional.of(1));
        when(twoFactor.verifySignIn(userId, "123456")).thenReturn(false);

        assertThat(at(START).answer(request, new MockHttpServletResponse(), "123456")).isEqualTo(TwoFactorSignIn.Result.WRONG_CODE);
        verify(completion).failSecondStep(request, userId, LoginEvent.Method.PASSWORD);
    }
}
