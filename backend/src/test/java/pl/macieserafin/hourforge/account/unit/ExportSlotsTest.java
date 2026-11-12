package pl.macieserafin.hourforge.account.unit;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.macieserafin.hourforge.account.ExportSlots;
import pl.macieserafin.hourforge.security.RateLimitProperties;
import pl.macieserafin.hourforge.security.RateLimiter;
import pl.macieserafin.hourforge.security.TooManyRequestsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportSlotsTest {
    private final RateLimiter limiter = new RateLimiter(Clock.systemUTC());
    private final RateLimitProperties limits = mock(RateLimitProperties.class);

    ExportSlotsTest() {
        when(limits.dataExport()).thenReturn(new RateLimitProperties.Limit(3, Duration.ofHours(1), null));
    }

    @Test
    void onePersonGetsOneCopyAtATime() {
        var slots = new ExportSlots(2, limiter, limits);
        var ala = UUID.randomUUID();

        var first = slots.open(ala);
        assertThatThrownBy(() -> slots.open(ala)).isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(30));
        first.close();

        assertThatCode(() -> slots.open(ala).close()).doesNotThrowAnyException();
    }

    @Test
    void theInstanceWritesAtMostTheConfiguredNumberAtOnce() {
        var slots = new ExportSlots(2, limiter, limits);
        var a = slots.open(UUID.randomUUID());
        var b = slots.open(UUID.randomUUID());

        assertThatThrownBy(() -> slots.open(UUID.randomUUID())).isInstanceOf(TooManyRequestsException.class);
        a.close();
        a.close(); // closing twice frees only one slot
        var c = slots.open(UUID.randomUUID());
        assertThatThrownBy(() -> slots.open(UUID.randomUUID())).isInstanceOf(TooManyRequestsException.class);
        b.close();
        c.close();
    }

    @Test
    void aRefusalForABusySlotDoesNotUseUpTheHourlyLimit() {
        var slots = new ExportSlots(1, limiter, limits);
        var ala = UUID.randomUUID();
        var held = slots.open(UUID.randomUUID());
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> slots.open(ala)).isInstanceOf(TooManyRequestsException.class);
        }
        held.close();

        // All three copies of the hour are still there; the fourth is refused and frees its slot.
        for (int i = 0; i < 3; i++) slots.open(ala).close();
        assertThatThrownBy(() -> slots.open(ala)).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> slots.open(UUID.randomUUID()).close()).doesNotThrowAnyException();
    }
}
