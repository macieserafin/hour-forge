package pl.macieserafin.hourforge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.seed.users", havingValue = "true")
public class SeedConfig {
    @Bean
    ApplicationRunner seedUsers(UserRepository users, UserCredentialRepository credentials,
                                PasswordEncoder encoder, PlatformTransactionManager transactionManager,
                                @Value("${app.seed.user.email}") String email,
                                @Value("${app.seed.user.display-name}") String displayName,
                                @Value("${app.seed.user.password}") String password) {
        return arguments -> {
            Assert.hasText(email, "SEED_USER_EMAIL must not be blank");
            Assert.hasText(displayName, "SEED_USER_DISPLAY_NAME must not be blank");
            Assert.isTrue(password.length() >= 12 && password.getBytes(StandardCharsets.UTF_8).length <= 72,
                    "SEED_USER_PASSWORD must contain at least 12 characters and at most 72 UTF-8 bytes");
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                var normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
                var existing = users.findByEmailIgnoreCase(normalizedEmail);
                if (existing.isPresent()) {
                    Assert.state(credentials.existsById(existing.get().getId()),
                            "Seed email belongs to an existing user without credentials; choose another SEED_USER_EMAIL");
                    return;
                }
                var user = users.save(new User(normalizedEmail, displayName.strip()));
                credentials.save(new UserCredential(user, encoder.encode(password)));
            });
        };
    }
}
