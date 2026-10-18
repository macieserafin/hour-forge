package pl.macieserafin.hourforge.config;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.seed.users=true")
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class SeedConfigTests {
    @Autowired UserRepository users;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired @Qualifier("seedUsers") ApplicationRunner seedUsers;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void seedsTwoAccountsOnStartupAndPrintsTheirCredentials(CapturedOutput output) throws Exception {
        for (int index = 1; index <= 2; index++) {
            var user = users.findByEmailIgnoreCase("USER" + index + "@hourforge.local").orElseThrow();
            var hash = credentials.findById(user.getId()).orElseThrow().getPasswordHash();
            assertThat(user.getDisplayName()).isEqualTo(index == 1 ? "User One" : "User Two");
            assertThat(encoder.matches("Test-user-" + index + "-2026!", hash)).isTrue();
            assertThat(hash).isNotEqualTo("Test-user-" + index + "-2026!");
        }
        seedUsers.run(new DefaultApplicationArguments(new String[0]));
        assertThat(output.getOut())
                .contains("name=User One, email=user1@hourforge.local, password=Test-user-1-2026!")
                .contains("name=User Two, email=user2@hourforge.local, password=Test-user-2-2026!");
    }

    @Test
    void doesNotDuplicateOrRehashAccountsOnRerun() throws Exception {
        var user = users.findByEmailIgnoreCase("user1@hourforge.local").orElseThrow();
        var hash = credentials.findById(user.getId()).orElseThrow().getPasswordHash();
        long initialUsers = users.count();
        long initialCredentials = credentials.count();
        seedUsers.run(new DefaultApplicationArguments(new String[0]));
        seedUsers.run(new DefaultApplicationArguments(new String[0]));
        assertThat(users.count()).isEqualTo(initialUsers);
        assertThat(credentials.count()).isEqualTo(initialCredentials);
        assertThat(credentials.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
    }

    @Test
    void refusesToAssignPasswordToAnExistingUserWithoutCredentials(CapturedOutput output) {
        int outputStart = output.getOut().length();
        var user = users.findByEmailIgnoreCase("user2@hourforge.local").orElseThrow();
        credentials.deleteById(user.getId());
        credentials.flush();
        assertThatThrownBy(() -> seedUsers.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("existing user without credentials");
        assertThat(output.getOut().substring(outputStart)).doesNotContain("Test user:");
    }

    @Test
    void preservesChangedPasswordAndDoesNotPrintIncorrectCredentials(CapturedOutput output) throws Exception {
        int outputStart = output.getOut().length();
        var user = users.findByEmailIgnoreCase("user1@hourforge.local").orElseThrow();
        var changedHash = encoder.encode("Changed-test-password-2026!");
        jdbc.update("update user_credentials set password_hash = ? where user_id = ?", changedHash, user.getId());
        entityManager.clear();
        seedUsers.run(new DefaultApplicationArguments(new String[0]));
        assertThat(credentials.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(changedHash);
        assertThat(output.getOut().substring(outputStart))
                .contains("email=user1@hourforge.local, password was changed; existing credentials preserved")
                .doesNotContain("password=Test-user-1-2026!");
    }
}
