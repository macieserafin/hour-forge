package pl.macieserafin.hourforge.security.unit;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.macieserafin.hourforge.security.LoginAttemptService;
import pl.macieserafin.hourforge.security.RateLimitProperties;
import pl.macieserafin.hourforge.security.RateLimiter;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static pl.macieserafin.hourforge.support.UnitFixtures.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {
    @Mock UserRepository users;
    @Mock RateLimitProperties limits;
    private LoginAttemptService attempts;

    @BeforeEach
    void setup() {
        var window = Duration.ofMinutes(15);
        when(limits.loginAccountIp()).thenReturn(new RateLimitProperties.Limit(2, window, window));
        when(limits.loginAccount()).thenReturn(new RateLimitProperties.Limit(4, window, window));
        when(limits.loginIp()).thenReturn(new RateLimitProperties.Limit(20, window, window));
        attempts = new LoginAttemptService(new RateLimiter(CLOCK), limits, users);
    }

    @Test
    void spellingsResolvedToOneAccountShareFailuresAndTheSuccessReset() {
        var account = user();
        when(users.findByEmailIgnoreCase("first@example.test")).thenReturn(Optional.of(account));
        when(users.findByEmailIgnoreCase("second@example.test")).thenReturn(Optional.of(account));
        attempts.recordFailure("first@example.test", "ip");
        attempts.recordFailure("second@example.test", "ip");
        assertThat(attempts.blockedFor("first@example.test", "ip")).isPresent();
        assertThat(attempts.blockedFor("second@example.test", "ip")).isPresent();

        attempts.recordSuccess("second@example.test", "ip");
        assertThat(attempts.blockedFor("first@example.test", "ip")).isEmpty();
    }

    @Test
    void accountFailuresAreSharedAcrossSpellingsAndIpAddresses() {
        var account = user();
        when(users.findByEmailIgnoreCase("first@example.test")).thenReturn(Optional.of(account));
        when(users.findByEmailIgnoreCase("second@example.test")).thenReturn(Optional.of(account));
        for (int i = 0; i < 4; i++) {
            attempts.recordFailure(i % 2 == 0 ? "first@example.test" : "second@example.test", "ip-" + i);
        }
        assertThat(attempts.blockedFor("second@example.test", "new-ip")).isPresent();
        attempts.recordSuccess("first@example.test", "new-ip");
        assertThat(attempts.blockedFor("first@example.test", "another-ip")).isPresent();
    }

    @Test
    void unknownAddressesKeepTheirOwnNormalizedLimit() {
        attempts.recordFailure("  UNKNOWN@example.test  ", "ip");
        attempts.recordFailure("unknown@example.test", "ip");
        assertThat(attempts.blockedFor("Unknown@Example.Test", "ip")).isPresent();
        assertThat(attempts.blockedFor("someone-else@example.test", "ip")).isEmpty();
    }
}
