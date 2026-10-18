package pl.macieserafin.hourforge.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.seed.users=true",
        "app.seed.user.email=seed-test@example.com",
        "app.seed.user.display-name=Seed Test",
        "app.seed.user.password=Test-seed-password-2026"
})
@ActiveProfiles("test")
@Transactional
class SeedConfigTests {
    @Autowired UserRepository users;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired @Qualifier("seedUsers") ApplicationRunner seedUsers;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void seedsOnStartupAndDoesNotDuplicateOrOverwriteOnRerun() throws Exception {
        var user = users.findByEmailIgnoreCase("SEED-TEST@example.com").orElseThrow();
        var hash = credentials.findById(user.getId()).orElseThrow().getPasswordHash();
        assertThat(user.getDisplayName()).isEqualTo("Seed Test");
        assertThat(encoder.matches("Test-seed-password-2026", hash)).isTrue();
        long initialUsers = users.count();
        long initialCredentials = credentials.count();

        seedUsers.run(new DefaultApplicationArguments(new String[0]));
        seedUsers.run(new DefaultApplicationArguments(new String[0]));

        assertThat(users.count()).isEqualTo(initialUsers);
        assertThat(credentials.count()).isEqualTo(initialCredentials);
        assertThat(credentials.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
    }

    @Test
    void refusesToAssignPasswordToAnExistingUserWithoutCredentials() {
        var existing = users.saveAndFlush(new User("existing@example.com", "Existing"));
        var runner = new SeedConfig().seedUsers(users, credentials, encoder, transactionManager,
                "existing@example.com", "Seed", "Another-test-password");
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("existing user without credentials");
        assertThat(credentials.existsById(existing.getId())).isFalse();
    }

    @Test
    void refusesBlankOrTooLongSeedPasswordsBeforePersisting() {
        long initialUsers = users.count();
        for (String password : new String[]{"", "short", "a".repeat(73)}) {
            var runner = new SeedConfig().seedUsers(users, credentials, encoder, transactionManager,
                    "invalid-password@example.com", "Seed", password);
            assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(users.count()).isEqualTo(initialUsers);
    }

    @Test
    void existingPasswordIsNotReplacedWhenSeedPasswordChanges() throws Exception {
        var user = users.findByEmailIgnoreCase("seed-test@example.com").orElseThrow();
        var previousHash = credentials.findById(user.getId()).orElseThrow().getPasswordHash();
        var runner = new SeedConfig().seedUsers(users, credentials, encoder, transactionManager,
                "seed-test@example.com", "Changed name", "A-different-test-password");
        runner.run(new DefaultApplicationArguments(new String[0]));
        assertThat(credentials.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(previousHash);
        assertThat(users.findById(user.getId()).orElseThrow().getDisplayName()).isEqualTo("Seed Test");
    }
}
