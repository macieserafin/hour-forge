package pl.macieserafin.hourforge.jira;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts OAuth tokens with AES-256-GCM before they are stored. The result is {@code version:iv:ciphertext}.
 * The {@code context} (for example the user and the Jira site) is authenticated but not stored, so a ciphertext
 * copied to another row no longer decrypts. Older key versions stay available to read until the data is re-encrypted.
 */
public class TokenCipher {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final String activeVersion;
    private final Map<String, SecretKey> keys = new HashMap<>();

    public TokenCipher(String activeVersion, String activeKey, Map<String, String> previousKeys) {
        this.activeVersion = activeVersion;
        keys.put(activeVersion, key(activeKey));
        previousKeys.forEach((version, key) -> keys.putIfAbsent(version, key(key)));
    }

    public String activeVersion() {
        return activeVersion;
    }

    public String encrypt(String plaintext, String context) {
        try {
            var iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeVersion), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            var encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            var base64 = Base64.getEncoder();
            return activeVersion + ":" + base64.encodeToString(iv) + ":" + base64.encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Token encryption failed", exception);
        }
    }

    public String decrypt(String stored, String context) {
        var parts = stored.split(":", 3);
        if (parts.length != 3) throw new IllegalArgumentException("Malformed encrypted token");
        var key = keys.get(parts[0]);
        if (key == null) throw new IllegalArgumentException("Unknown token encryption key version");
        try {
            var base64 = Base64.getDecoder();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, base64.decode(parts[1])));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(base64.decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            // The reason is deliberately vague: callers must not learn whether the data or the context was wrong.
            throw new IllegalArgumentException("Encrypted token cannot be read", exception);
        }
    }

    public String versionOf(String stored) {
        int colon = stored.indexOf(':');
        return colon < 0 ? "" : stored.substring(0, colon);
    }

    /** True when the value was written with an older key and should be rewritten with the active one. */
    public boolean needsRewrap(String stored) {
        return !activeVersion.equals(versionOf(stored));
    }

    private static SecretKey key(String base64) {
        var bytes = Base64.getDecoder().decode(base64.strip());
        if (bytes.length != 32) throw new IllegalArgumentException("A token encryption key must be 32 bytes");
        return new SecretKeySpec(bytes, "AES");
    }
}
