package pl.macieserafin.hourforge.jobs;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Background jobs: whether they run, how long data is kept, and when a timer counts as forgotten. */
@ConfigurationProperties("app.jobs")
public record JobsProperties(boolean enabled, Duration unconfirmedAccountAge, Duration spentTokenAge, Duration staleInvitationAge,
                             Duration longTimerAfter) {
    public JobsProperties {
        unconfirmedAccountAge = positive(unconfirmedAccountAge, Duration.ofDays(7));
        spentTokenAge = positive(spentTokenAge, Duration.ofDays(7));
        staleInvitationAge = positive(staleInvitationAge, Duration.ofDays(90));
        longTimerAfter = positive(longTimerAfter, Duration.ofHours(10));
    }

    private static Duration positive(Duration value, Duration fallback) {
        if (value == null) return fallback;
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException("Job durations must be positive");
        return value;
    }
}
