package pl.macieserafin.hourforge.user.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.macieserafin.hourforge.user.EmailAddress;

import static org.assertj.core.api.Assertions.assertThat;

class EmailAddressTest {
    @ParameterizedTest
    @CsvSource({
            "'  User@EXAMPLE.test  ', user@example.test",
            "'\u2003User@EXAMPLE.test\u2003', user@example.test",
            "'İDA@EXAMPLE.test', 'i\u0307da@example.test'",
            "'vıctim-iſ@EXAMPLE.test', 'vıctim-iſ@example.test'",
            "'ΟΣ@EXAMPLE.test', 'ος@example.test'"
    })
    void normalizesCaseAndWhitespaceWithoutFoldingDistinctLetters(String input, String expected) {
        assertThat(EmailAddress.normalize(input)).isEqualTo(expected);
        assertThat(EmailAddress.normalize(expected)).isEqualTo(expected);
    }

    @Test
    void trimsBothAuthenticationFilterPaddingAndUnicodeWhitespaceInOnePass() {
        var input = "\u2003\u0001 User@EXAMPLE.test \u0002\u2003";
        assertThat(EmailAddress.normalize(input)).isEqualTo("user@example.test");
    }

    @Test
    void preservesMissingInputForValidation() {
        assertThat(EmailAddress.normalize(null)).isNull();
        assertThat(EmailAddress.normalize("   ")).isEmpty();
    }
}
