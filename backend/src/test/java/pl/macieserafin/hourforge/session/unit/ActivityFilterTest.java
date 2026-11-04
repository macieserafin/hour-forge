package pl.macieserafin.hourforge.session.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.session.ActivityFilter;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActivityFilterTest {
    private static final Instant MORNING = Instant.parse("2026-11-04T08:00:00Z");
    private static final Instant TODAY = Instant.parse("2026-11-04T00:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final MutableClock clock = new MutableClock(MORNING);
    private final ActivityFilter filter = new ActivityFilter(users, clock);

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aPersonIsRecordedOnceADayWithoutTouchingTheSession() throws Exception {
        var userId = signIn();
        var request = new MockHttpServletRequest();
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        clock.now = MORNING.plusSeconds(3600);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        verify(users, times(1)).markActive(userId, TODAY);
        assertThat(request.getSession(false)).isNull();

        clock.now = MORNING.plusSeconds(24 * 3600);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
        verify(users).markActive(userId, TODAY.plusSeconds(24 * 3600));
    }

    @Test
    void aFailedUpdateIsTriedAgainOnTheNextRequest() throws Exception {
        var userId = signIn();
        when(users.markActive(userId, TODAY)).thenThrow(new IllegalStateException("database down")).thenReturn(1);

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain()))
                .isInstanceOf(IllegalStateException.class);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        verify(users, times(2)).markActive(userId, TODAY);
    }

    @Test
    void anonymousRequestsRecordNothing() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
        verifyNoInteractions(users);
    }

    @Test
    void eachPersonIsRecordedForThemselves() throws Exception {
        var first = signIn();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
        var second = signIn();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        verify(users).markActive(first, TODAY);
        verify(users).markActive(second, TODAY);
        verify(users, times(2)).markActive(any(), any());
    }

    private UUID signIn() {
        var userId = UUID.randomUUID();
        var principal = new AppUserPrincipal(userId, userId + "@example.com", "hash", 0);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        return userId;
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
