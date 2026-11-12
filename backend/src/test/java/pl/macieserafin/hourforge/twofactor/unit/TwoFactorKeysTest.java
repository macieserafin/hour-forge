package pl.macieserafin.hourforge.twofactor.unit;

import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.macieserafin.hourforge.twofactor.TwoFactorKeys;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwoFactorKeysTest {
    private static final String KEY_A = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="; // gitleaks:allow (test-only key)
    private static final String KEY_B = "HxwdHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="; // gitleaks:allow (test-only key)

    @Test
    void noKeyMeansTwoStepSignInIsUnavailableButValid() {
        assertThatCode(() -> new TwoFactorKeys("", null, null)).doesNotThrowAnyException();
    }

    @Test
    void aRotationKeepsTheOldKeyUnderItsVersion() {
        assertThatCode(() -> new TwoFactorKeys(KEY_B, "v2", Map.of("v1", KEY_A))).doesNotThrowAnyException();
    }

    @Test
    void malformedOrConflictingKeysStopTheStart() {
        assertThatThrownBy(() -> new TwoFactorKeys("short", "v1", null))
                .hasMessageContaining("TWO_FACTOR_ENCRYPTION_KEY (32 bytes");
        assertThatThrownBy(() -> new TwoFactorKeys(KEY_B, "v2", Map.of("v1", "not-base64!")))
                .hasMessageContaining("previous two-step sign-in key v1");
        assertThatThrownBy(() -> new TwoFactorKeys(null, "v2", Map.of("v1", KEY_A)))
                .hasMessageContaining("no active one");
        assertThatThrownBy(() -> new TwoFactorKeys(KEY_B, "v1", Map.of("v1", KEY_A)))
                .hasMessageContaining("raise it");
    }
}
