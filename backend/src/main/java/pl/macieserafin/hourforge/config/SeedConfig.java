package pl.macieserafin.hourforge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.util.ArrayList;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.seed.users", havingValue = "true")
public class SeedConfig {
    private static final Logger log = LoggerFactory.getLogger(SeedConfig.class);
    private static final List<SeedUser> SEED_USERS = List.of(
            new SeedUser("User One", "user1@hourforge.local", "Test-user-1-2026!"),
            new SeedUser("User Two", "user2@hourforge.local", "Test-user-2-2026!")
    );

    @Bean
    ApplicationRunner seedUsers(UserRepository users, UserCredentialRepository credentials,
                                PasswordEncoder encoder, PlatformTransactionManager transactionManager) {
        return arguments -> {
            var seededUsers = new TransactionTemplate(transactionManager).execute(status -> {
                var result = new ArrayList<SeedUser>();
                for (var seed : SEED_USERS) {
                    var existing = users.findByEmailIgnoreCase(seed.email());
                    if (existing.isPresent()) {
                        var user = existing.get();
                        var credential = credentials.findById(user.getId()).orElseThrow(() ->
                                new IllegalStateException("Seed email belongs to an existing user without credentials: "
                                        + seed.email()));
                        var password = encoder.matches(seed.password(), credential.getPasswordHash())
                                ? seed.password() : null;
                        result.add(new SeedUser(user.getDisplayName(), user.getEmail(), password));
                    } else {
                        var user = users.save(new User(seed.email(), seed.displayName()));
                        credentials.save(new UserCredential(user, encoder.encode(seed.password())));
                        result.add(seed);
                    }
                }
                return result;
            });
            for (var user : seededUsers) {
                if (user.password() != null) {
                    log.info("Test user: name={}, email={}, password={}",
                            user.displayName(), user.email(), user.password());
                } else {
                    log.info("Test user: name={}, email={}, password was changed; existing credentials preserved",
                            user.displayName(), user.email());
                }
            }
        };
    }

    private record SeedUser(String displayName, String email, String password) {}
}
