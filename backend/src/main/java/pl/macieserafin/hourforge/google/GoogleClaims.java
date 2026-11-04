package pl.macieserafin.hourforge.google;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import pl.macieserafin.hourforge.user.EmailAddress;

import java.util.Locale;

/**
 * What the sign-in uses from a verified ID token. The {@code sub} is the only key to an account and is compared exactly;
 * the address only fills a new account and is shown in the account settings.
 */
record GoogleClaims(String subject, String email, boolean emailVerified, String hostedDomain, String name) {
    static final int MAX_LENGTH = 255;

    static GoogleClaims of(OidcUser user) {
        var email = EmailAddress.normalize(user.getEmail());
        return new GoogleClaims(user.getSubject(), email == null || email.isEmpty() ? null : email,
                Boolean.TRUE.equals(user.getEmailVerified()), user.getClaimAsString("hd"), user.getFullName());
    }

    boolean usableSubject() {
        return subject != null && !subject.isEmpty() && subject.length() <= MAX_LENGTH;
    }

    /** A verified address short enough to be an account's address. */
    boolean usableEmail() {
        return emailVerified && email != null && email.length() <= MAX_LENGTH && email.indexOf('@') > 0 && email.indexOf('@') < email.length() - 1;
    }

    /** The address to remember on the link, when there is one that fits. */
    String emailForDisplay() {
        return email != null && email.length() <= MAX_LENGTH ? email : null;
    }

    /**
     * Google vouches for the address only for Gmail and for Workspace accounts ({@code hd}) with a verified address;
     * only then does the new account count as confirmed (A13-D4).
     */
    boolean authoritative() {
        return usableEmail() && (email.toLowerCase(Locale.ROOT).endsWith("@gmail.com") || (hostedDomain != null && !hostedDomain.isBlank()));
    }

    /** The name Google gives, or the part of the address before the {@code @}. */
    String displayName() {
        var given = name == null ? "" : name.strip();
        if (given.isEmpty()) given = email.substring(0, email.indexOf('@'));
        return given.codePointCount(0, given.length()) <= MAX_LENGTH ? given
                : given.substring(0, given.offsetByCodePoints(0, MAX_LENGTH)).strip();
    }
}
