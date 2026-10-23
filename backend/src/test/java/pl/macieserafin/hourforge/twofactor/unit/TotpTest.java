package pl.macieserafin.hourforge.twofactor.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.macieserafin.hourforge.twofactor.Totp;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {
    // The SHA1 secret of RFC 6238 appendix B ("12345678901234567890"), Base32-encoded.
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @ParameterizedTest
    @CsvSource({"59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924", "2000000000, 279037", "20000000000, 353130"})
    void matchesTheTestVectorsOfRfc6238(long seconds, String expected) {
        // The RFC lists eight digits; the last six are the six-digit code.
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(seconds)))).isEqualTo(expected);
    }

    @Test
    void acceptsTheCurrentAndNeighbouringStepsOnly() {
        var now = Instant.ofEpochSecond(1_111_111_111);
        long step = Totp.step(now);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step), now, null)).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, null)).hasValue(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now, null)).isEmpty();
    }

    @Test
    void aCodeIsNeverAcceptedTwice() {
        var now = Instant.ofEpochSecond(1_111_111_111);
        long step = Totp.step(now);
        var code = Totp.code(RFC_SECRET, step);
        assertThat(Totp.verify(RFC_SECRET, code, now, step)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, step)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"''", "12345", "1234567", "abcdef", "12 345"})
    void rejectsAnythingButSixDigits(String code) {
        assertThat(Totp.verify(RFC_SECRET, code, Instant.ofEpochSecond(59), null)).isEmpty();
    }

    @Test
    void newSecretsAreRandom160BitBase32Values() {
        var secret = Totp.newSecret();
        assertThat(secret).matches("[A-Z2-7]{32}").isNotEqualTo(Totp.newSecret());
    }

    @Test
    void aSecretTypedInLowerCaseOrWithSpacesStillWorks() {
        assertThat(Totp.code("gezd gnbv gy3t qojq gezd gnbv gy3t qojq", 1)).isEqualTo(Totp.code(RFC_SECRET, 1));
    }

    @Test
    void theAppLinkNamesTheServiceAndTheAccount() {
        assertThat(Totp.uri("HourForge", "ana+work@example.com", "ABC"))
                .isEqualTo("otpauth://totp/HourForge:ana%2Bwork%40example.com?secret=ABC&issuer=HourForge&algorithm=SHA1&digits=6&period=30");
    }
}
