package pl.macieserafin.hourforge.auth.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.auth.PasswordPolicy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {
    @Test
    void acceptsTwelveCharactersAndSeventyTwoBytes() {
        assertThatCode(() -> PasswordPolicy.require("a".repeat(12))).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.require("a".repeat(72))).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.require("ą".repeat(36))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "            ", "elevenchars"})
    void rejectsMissingBlankAndShortPasswords(String password) {
        assertThatThrownBy(() -> PasswordPolicy.require(password)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsMoreThanSeventyTwoBytes() {
        assertThatThrownBy(() -> PasswordPolicy.require("a".repeat(73))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> PasswordPolicy.require("ą".repeat(37))).isInstanceOf(ResponseStatusException.class);
    }
}
