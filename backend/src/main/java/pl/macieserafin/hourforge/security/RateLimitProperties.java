package pl.macieserafin.hourforge.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(Limit loginAccountIp, Limit loginIp, Limit loginAccount, Limit registration,
                                  Limit teamCreation, Limit invitationCreation, Limit projectCreation,
                                  Limit passwordChange, Limit emailVerification, Limit emailVerificationIp,
                                  Limit dataExport, Limit reportExport, Limit jiraAuthorize) {
    /** At most {@code max} events within {@code window}; failure limits then block for {@code block}. */
    public record Limit(int max, Duration window, Duration block) {
        public Limit {
            if (max < 1 || window == null || window.isNegative() || window.isZero()
                    || (block != null && (block.isNegative() || block.isZero()))) {
                throw new IllegalArgumentException("Rate limit needs max >= 1 and positive window and block durations");
            }
        }
    }
}
