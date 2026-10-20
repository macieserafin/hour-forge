package pl.macieserafin.hourforge.jira;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {
    private static String key() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private final TokenCipher cipher = new TokenCipher("v1", key(), Map.of());

    @Test
    void roundTripsAndNeverStoresPlaintext() {
        var stored = cipher.encrypt("secret-access-token", "user-1:cloud-1");
        assertThat(stored).doesNotContain("secret-access-token").startsWith("v1:");
        assertThat(cipher.decrypt(stored, "user-1:cloud-1")).isEqualTo("secret-access-token");
    }

    @Test
    void usesFreshIvEachTime() {
        assertThat(cipher.encrypt("same", "ctx")).isNotEqualTo(cipher.encrypt("same", "ctx"));
    }

    @Test
    void rejectsAnotherContext() {
        var stored = cipher.encrypt("token", "user-1:cloud-1");
        assertThatThrownBy(() -> cipher.decrypt(stored, "user-2:cloud-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTamperedCiphertext() {
        var stored = cipher.encrypt("token", "ctx");
        var parts = stored.split(":");
        var bytes = Base64.getDecoder().decode(parts[2]);
        bytes[0] ^= 1;
        var tampered = parts[0] + ":" + parts[1] + ":" + Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> cipher.decrypt(tampered, "ctx")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedAndUnknownVersion() {
        assertThatThrownBy(() -> cipher.decrypt("garbage", "ctx")).isInstanceOf(IllegalArgumentException.class);
        var stored = cipher.encrypt("token", "ctx").replaceFirst("^v1", "v9");
        assertThatThrownBy(() -> cipher.decrypt(stored, "ctx")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsOlderKeyVersionsAfterRotation() {
        var oldKey = key();
        var old = new TokenCipher("v1", oldKey, Map.of());
        var stored = old.encrypt("token", "ctx");
        var rotated = new TokenCipher("v2", key(), Map.of("v1", oldKey));
        assertThat(rotated.decrypt(stored, "ctx")).isEqualTo("token");
        assertThat(rotated.needsRewrap(stored)).isTrue();
        assertThat(rotated.needsRewrap(rotated.encrypt("token", "ctx"))).isFalse();
    }

    @Test
    void rejectsKeysOfTheWrongSize() {
        var shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new TokenCipher("v1", shortKey, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
