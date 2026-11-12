package pl.macieserafin.hourforge.twofactor;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import pl.macieserafin.hourforge.jira.TokenCipher;

/**
 * Keys that encrypt the authenticator secrets: the active one ({@code TWO_FACTOR_ENCRYPTION_KEY}, version
 * {@code TWO_FACTOR_ENCRYPTION_KEY_VERSION}) and older ones kept for reading during a rotation
 * ({@code APP_TWOFACTOR_PREVIOUSENCRYPTIONKEYS_V1=<old key>}). Without an active key two-step sign-in cannot be turned
 * on. A malformed key stops the application at startup instead of failing at the first sign-in.
 */
@ConfigurationProperties("app.two-factor")
public record TwoFactorKeys(String encryptionKey, String encryptionKeyVersion, Map<String, String> previousEncryptionKeys) {

    public TwoFactorKeys {
        encryptionKey = blank(encryptionKey) ? null : encryptionKey.strip();
        encryptionKeyVersion = blank(encryptionKeyVersion) ? "v1" : encryptionKeyVersion.strip();
        previousEncryptionKeys = previousEncryptionKeys == null ? Map.of() : Map.copyOf(previousEncryptionKeys);
        var problems = new ArrayList<String>();
        if (encryptionKey != null && !validKey(encryptionKey)) problems.add("TWO_FACTOR_ENCRYPTION_KEY (32 bytes, base64)");
        previousEncryptionKeys.forEach((version, key) -> {
            if (!validKey(key)) problems.add("previous two-step sign-in key " + version + " (32 bytes, base64)");
        });
        if (encryptionKey == null && !previousEncryptionKeys.isEmpty()) {
            problems.add("TWO_FACTOR_ENCRYPTION_KEY (previous keys are set, but no active one)");
        }
        if (previousEncryptionKeys.containsKey(encryptionKeyVersion)) {
            problems.add("TWO_FACTOR_ENCRYPTION_KEY_VERSION (" + encryptionKeyVersion + " is also a previous key; raise it)");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Two-step sign-in keys are invalid: " + String.join(", ", problems));
        }
    }

    /** The cipher, or null without an active key (two-step sign-in is then unavailable). */
    TokenCipher cipher() {
        return encryptionKey == null ? null : new TokenCipher(encryptionKeyVersion, encryptionKey, previousEncryptionKeys);
    }

    private static boolean validKey(String key) {
        if (blank(key)) return false;
        try {
            return Base64.getDecoder().decode(key.strip()).length == 32;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
