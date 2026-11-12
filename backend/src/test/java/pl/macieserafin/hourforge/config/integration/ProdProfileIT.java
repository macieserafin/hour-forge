package pl.macieserafin.hourforge.config.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
@TestPropertySource(properties = "DB_STATEMENT_TIMEOUT_MS=500")
class ProdProfileIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
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

    @Test
    void apiAnswersCarryTheBrowserFeaturePolicy() throws Exception {
        mvc.perform(get("/api/config"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }
}
