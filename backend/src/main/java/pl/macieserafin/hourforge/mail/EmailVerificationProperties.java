package pl.macieserafin.hourforge.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** When {@code required} is set, accounts must confirm their address before they can sign in. */
@ConfigurationProperties("app.email-verification")
public record EmailVerificationProperties(boolean required) {
}
