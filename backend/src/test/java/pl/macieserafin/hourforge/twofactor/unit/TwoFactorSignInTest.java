package pl.macieserafin.hourforge.twofactor.unit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pl.macieserafin.hourforge.security.DatabaseUserDetailsService;
import pl.macieserafin.hourforge.session.SignInCompletion;
import pl.macieserafin.hourforge.twofactor.TwoFactorService;
import pl.macieserafin.hourforge.twofactor.TwoFactorSignIn;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TwoFactorSignInTest {
    private static final Instant START = Instant.parse("2026-10-23T10:00:00Z");

    private final TwoFactorService twoFactor = mock(TwoFactorService.class);
    private final SignInCompletion completion = mock(SignInCompletion.class);

    private TwoFactorSignIn at(Instant now) {
        return new TwoFactorSignIn(twoFactor, mock(DatabaseUserDetailsService.class), mock(UserRepository.class), completion,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void theCodeIsAwaitedForFiveMinutesOnly() {
        var request = new MockHttpServletRequest();
        at(START).begin(request, UUID.randomUUID(), false);

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
        at(START).begin(request, UUID.randomUUID(), true);
        assertThat(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
    }
}
