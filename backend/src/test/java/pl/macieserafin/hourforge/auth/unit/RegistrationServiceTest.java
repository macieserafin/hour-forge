package pl.macieserafin.hourforge.auth.unit;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.auth.*;
import pl.macieserafin.hourforge.user.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pl.macieserafin.hourforge.support.UnitFixtures.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {
    @Mock UserRepository users;
    @Mock UserCredentialRepository credentials;
    @Mock PasswordEncoder encoder;
    private RegistrationService service;

    @BeforeEach void setup() { service = new RegistrationService(users, credentials, encoder); }

    @Test void normalizesIdentityAndPersistsOnlyTheEncodedPasswordWithoutTrimmingIt() {
        var request = new RegistrationRequest("  New User  ", "  New@Example.test  ", " password 2026! ");
        when(encoder.encode(request.password())).thenReturn("encoded-password");
        when(users.saveAndFlush(any())).thenAnswer(call -> identified(call.getArgument(0)));
        service.register(request);
        var saved = ArgumentCaptor.forClass(UserCredential.class);
        verify(credentials).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(saved.getValue().getUser().getEmail()).isEqualTo("new@example.test");
        assertThat(saved.getValue().getUser().getDisplayName()).isEqualTo("New User");
        assertThat(request.toString()).doesNotContain(request.password());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "            ", "😀😀😀😀😀😀😀😀😀😀😀"})
    void rejectsInvalidPasswordsBeforeAccessingTheDatabase(String password) {
        assertThatThrownBy(() -> service.register(new RegistrationRequest("New User", "new@example.test", password)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(users, credentials, encoder);
    }

    @Test void enforcesTheBcryptByteLimitForUnicodeAndAscii() {
        for (String password : new String[]{"a".repeat(73), "ą".repeat(37), "😀".repeat(19)}) {
            assertThatThrownBy(() -> service.register(new RegistrationRequest("New User", "new@example.test", password)))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        }
        verifyNoInteractions(users, credentials, encoder);
    }

    @Test void doesNotOverwriteAnExistingAccountOrItsPassword() {
        when(users.findByEmailIgnoreCase("new@example.test")).thenReturn(Optional.of(user()));
        assertThatThrownBy(() -> service.register(new RegistrationRequest("New User", "new@example.test", "Valid-password-2026!")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(encoder, credentials);
    }
}
