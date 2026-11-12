package pl.macieserafin.hourforge.twofactor.integration;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import pl.macieserafin.hourforge.jira.TokenCipher;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.twofactor.Totp;
import pl.macieserafin.hourforge.twofactor.TwoFactorService;
import pl.macieserafin.hourforge.twofactor.UserTwoFactor;
import pl.macieserafin.hourforge.twofactor.UserTwoFactorRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** After a rotation the old key still reads the secrets, and each secret is written again with the new key. */
@TestPropertySource(properties = {
        "app.two-factor.encryption-key=" + TwoFactorKeyRotationIT.NEW_KEY,
        "app.two-factor.encryption-key-version=v2",
        "app.two-factor.previous-encryption-keys.v1=" + TwoFactorKeyRotationIT.OLD_KEY})
class TwoFactorKeyRotationIT extends ApiIntegrationTestSupport {
    static final String OLD_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="; // gitleaks:allow (test-only key)
    static final String NEW_KEY = "HxwdHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="; // gitleaks:allow (test-only key)
    private final TokenCipher oldCipher = new TokenCipher("v1", OLD_KEY, Map.of());
    private final TokenCipher newCipher = new TokenCipher("v2", NEW_KEY, Map.of());

    @Autowired TwoFactorService twoFactor;
    @Autowired UserTwoFactorRepository secrets;

    @Test
    void aSignInWithASecretFromTheOldKeyWorksAndRewritesIt() {
        var user = fixtures.user("rotation-sign-in@example.com", "Rotation");
        var secret = Totp.newSecret();
        enabledSecret(user.getId(), oldCipher.encrypt(secret, "two-factor:" + user.getId()));

        assertThat(twoFactor.verifySignIn(user.getId(), Totp.code(secret, Totp.step(Instant.now())))).isTrue();

        var stored = secrets.findById(user.getId()).orElseThrow().getSecret();
        assertThat(stored).startsWith("v2:");
        assertThat(newCipher.decrypt(stored, "two-factor:" + user.getId())).isEqualTo(secret);
    }

    @Test
    void theStartRewritesEverySecretOfAnOlderKey() {
        var rotated = fixtures.user("rotation-start@example.com", "Rotation");
        var secret = Totp.newSecret();
        enabledSecret(rotated.getId(), oldCipher.encrypt(secret, "two-factor:" + rotated.getId()));
        var lost = fixtures.user("rotation-lost@example.com", "Lost key");
        var unknown = new TokenCipher("v0", "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=", Map.of()) // gitleaks:allow (test-only key)
                .encrypt(Totp.newSecret(), "two-factor:" + lost.getId());
        enabledSecret(lost.getId(), unknown);

        assertThat(twoFactor.rewrapWithActiveKey()).isPositive();

        assertThat(newCipher.decrypt(secrets.findById(rotated.getId()).orElseThrow().getSecret(), "two-factor:" + rotated.getId()))
                .isEqualTo(secret);
        // A secret whose key is gone stays as it was; the person signs in with a backup code or the operator turns it off.
        assertThat(secrets.findById(lost.getId()).orElseThrow().getSecret()).isEqualTo(unknown);
    }

    private void enabledSecret(java.util.UUID userId, String encrypted) {
        var row = new UserTwoFactor(userId, encrypted, Instant.now());
        row.enable(Instant.now());
        secrets.saveAndFlush(row);
    }
}
