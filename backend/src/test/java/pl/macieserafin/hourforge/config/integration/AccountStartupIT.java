package pl.macieserafin.hourforge.config.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.seed.users=true")
class AccountStartupIT extends IntegrationTestSupport {
    @Autowired UserRepository users;
    @Autowired ApplicationContext context;

    @Test
    void doesNotCreateDefaultAccountsEvenWithLegacySeedFlag() {
        assertThat(context.containsBean("seedUsers")).isFalse();
        assertThat(users.findByEmailIgnoreCase("user1@hourforge.local")).isEmpty();
        assertThat(users.findByEmailIgnoreCase("user2@hourforge.local")).isEmpty();
    }
}
