package pl.macieserafin.hourforge.config.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests never read the developer's {@code .env}: its values (real OAuth clients, keys) would change what the tests check
 * and could end up in test reports. Only meaningful on a machine with a {@code .env}; elsewhere there is nothing to read.
 */
class EnvFileIsolationIT extends IntegrationTestSupport {
    @Autowired ConfigurableEnvironment environment;

    @Test
    void theEnvFileIsNotAPropertySourceOfTheTests() {
        var names = StreamSupport.stream(environment.getPropertySources().spliterator(), false).map(PropertySource::getName).toList();

        assertThat(names).noneMatch(name -> name.contains(".env"));
    }
}
