package pl.macieserafin.hourforge.security.unit;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.security.DatabaseUserDetailsService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pl.macieserafin.hourforge.support.UnitFixtures.*;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {
    @Mock UserCredentialRepository credentials;
    @InjectMocks DatabaseUserDetailsService service;
    @Test void trimsEmailAndMapsDatabaseIdentityWithoutGlobalTeamRoles() {
        var user = user();
        when(credentials.findByUserEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(new UserCredential(user, "hash")));
        var principal = (AppUserPrincipal) service.loadUserByUsername("  " + user.getEmail() + "  ");
        assertThat(principal.getUserId()).isEqualTo(user.getId());
        assertThat(principal.getPassword()).isEqualTo("hash");
        assertThat(principal.getAuthorities()).isEmpty();
    }
    @Test void hidesWhetherCredentialsExist() {
        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.test"))
                .isInstanceOf(UsernameNotFoundException.class).hasMessage("Invalid credentials");
    }
}
