package pl.macieserafin.hourforge.auth.unit;

import org.junit.jupiter.api.Test;
import pl.macieserafin.hourforge.auth.EmailTokenService;
import pl.macieserafin.hourforge.auth.EmailVerificationService;
import pl.macieserafin.hourforge.mail.DisabledEmailSender;
import pl.macieserafin.hourforge.mail.EmailVerificationProperties;
import pl.macieserafin.hourforge.security.RateLimitProperties;
import pl.macieserafin.hourforge.security.RateLimiter;
import pl.macieserafin.hourforge.support.RecordingEmailSender;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class EmailVerificationServiceTest {
    private static final RateLimitProperties.Limit LIMIT = new RateLimitProperties.Limit(1, Duration.ofHours(1), null);

    private EmailVerificationService service(boolean required, boolean mailEnabled) {
        var limits = new RateLimitProperties(LIMIT, LIMIT, LIMIT, LIMIT, LIMIT, LIMIT, LIMIT, LIMIT, LIMIT, LIMIT);
        return new EmailVerificationService(mock(UserRepository.class), mock(EmailTokenService.class),
                mailEnabled ? new RecordingEmailSender() : new DisabledEmailSender(), new EmailVerificationProperties(required),
                new RateLimiter(Clock.systemUTC()), limits, Clock.systemUTC(), "http://localhost:5173/");
    }

    @Test
    void refusesToStartWhenConfirmationIsRequiredButMailIsNotConfigured() {
        assertThatThrownBy(() -> service(true, false)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_MAIL_HOST");
    }

    @Test
    void startsWithoutMailWhenConfirmationIsOptional() {
        assertThatCode(() -> service(false, false)).doesNotThrowAnyException();
        assertThatCode(() -> service(true, true)).doesNotThrowAnyException();
    }
}
