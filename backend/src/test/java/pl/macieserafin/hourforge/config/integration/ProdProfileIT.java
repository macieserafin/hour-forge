package pl.macieserafin.hourforge.config.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.ConsoleAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/** The {@code prod} profile turns on the safe production values without any extra setting. */
@ActiveProfiles({"test", "prod"})
// The test database has no separate migration step, so this context migrates it itself.
@TestPropertySource(properties = {"DB_STATEMENT_TIMEOUT_MS=500", "spring.flyway.enabled=true"})
class ProdProfileIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConfigurableEnvironment environment;
    @Value("${server.servlet.session.cookie.secure}") boolean secureCookie;
    @Value("${server.forward-headers-strategy}") String forwardHeaders;

    @Test
    void sessionCookieIsSecureAndOnlyTheProxyIsTrustedForClientAddresses() {
        assertThat(secureCookie).isTrue();
        assertThat(forwardHeaders).isEqualTo("native");
    }

    @Test
    void aStatementRunningTooLongIsCancelledByTheDatabase() {
        assertThatThrownBy(() -> jdbc.execute("select pg_sleep(5)"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("statement timeout");
    }

    /**
     * The logging setup is global to the JVM and every test context sets it up again, so this test initializes it with
     * the environment of this context itself, checks it, and leaves the plain setup for the tests that follow.
     */
    @Test
    void theLogIsWrittenAsJsonWithRedaction() {
        var logging = LoggingSystem.get(getClass().getClassLoader());
        try {
            reinitialize(logging, environment);
            var root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            var appender = (ConsoleAppender<?>) root.getAppender("JSON");
            assertThat(appender).isNotNull();
            assertThat(appender.getEncoder()).isInstanceOf(StructuredLogEncoder.class);
            assertThat(root.getAppender("CONSOLE")).isNull();
        } finally {
            reinitialize(logging, new StandardEnvironment());
        }
    }

    private static void reinitialize(LoggingSystem logging, ConfigurableEnvironment environment) {
        logging.cleanUp();
        logging.beforeInitialize();
        logging.initialize(new LoggingInitializationContext(environment), null, null);
    }

    @Test
    void apiAnswersCarryTheBrowserFeaturePolicy() throws Exception {
        mvc.perform(get("/api/config"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }
}
