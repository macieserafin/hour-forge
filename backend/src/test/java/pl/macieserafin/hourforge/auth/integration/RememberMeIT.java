package pl.macieserafin.hourforge.auth.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.support.BrowserSession;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RememberMeIT extends ApiIntegrationTestSupport {
    private static final String EMAIL = "remember@example.com";
    private static final String PASSWORD = "Test-password-2026";
    private static final Duration THIRTY_DAYS = Duration.ofDays(30);

    @Autowired MockMvc mvc;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired FindByIndexNameSessionRepository<? extends Session> sessions;
    @Value("${server.servlet.session.timeout}") Duration sessionTimeout;

    @BeforeEach
    void createUser() {
        var user = fixtures.user(EMAIL, "Remember");
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
    }

    @Test
    void aNormalSessionEndsAfterEightHoursOfInactivity() throws Exception {
        assertThat(sessionTimeout).isEqualTo(Duration.ofHours(8));
        var browser = new BrowserSession(mvc);
        browser.login(EMAIL, PASSWORD).andExpect(status().isNoContent());
        assertThat(storedTimeout(browser)).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void rememberMeKeepsTheSessionForThirtyDaysWithAPersistentCookie() throws Exception {
        var browser = new BrowserSession(mvc);
        var response = browser.login(EMAIL, PASSWORD, true).andExpect(status().isNoContent()).andReturn().getResponse();

        assertThat(storedTimeout(browser)).isEqualTo(THIRTY_DAYS);
        var cookie = response.getHeaders("Set-Cookie").stream().filter(header -> header.startsWith("SESSION=")).findFirst().orElseThrow();
        assertThat(cookie).contains("Max-Age=2592000").contains("HttpOnly").contains("SameSite=Lax").contains("Path=/")
                .contains("SESSION=" + browser.cookie().getValue());
        browser.perform(get("/api/me")).andExpect(status().isOk());
    }

    @Test
    void withoutRememberMeTheSessionKeepsTheDefaultAndGetsNoPersistentCookie() throws Exception {
        var browser = new BrowserSession(mvc);
        var response = browser.login(EMAIL, PASSWORD).andExpect(status().isNoContent()).andReturn().getResponse();

        assertThat(storedTimeout(browser)).isNotEqualTo(THIRTY_DAYS);
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(header -> header.startsWith("SESSION="))
                .noneMatch(header -> header.contains("Max-Age"));
    }

    @Test
    void onlyTheExactValueTrueTurnsItOn() throws Exception {
        for (var value : new String[] {"1", "yes", "TRUE", "on", ""}) {
            var browser = new BrowserSession(mvc);
            browser.perform(post("/api/auth/login").with(csrf())
                            .param("email", EMAIL).param("password", PASSWORD).param("rememberMe", value))
                    .andExpect(status().isNoContent());
            assertThat(storedTimeout(browser)).isNotEqualTo(THIRTY_DAYS);
        }
    }

    @Test
    void aFailedLoginNeverExtendsAnything() throws Exception {
        var browser = new BrowserSession(mvc);
        var response = browser.perform(post("/api/auth/login").with(csrf())
                        .param("email", EMAIL).param("password", "wrong-password").param("rememberMe", "true"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertThat(response.getHeaders("Set-Cookie")).noneMatch(header -> header.contains("Max-Age=2592000"));
        if (browser.sessionId() != null) assertThat(storedTimeout(browser)).isNotEqualTo(THIRTY_DAYS);
    }

    @Test
    void signingOutExpiresTheRememberedCookie() throws Exception {
        var browser = new BrowserSession(mvc);
        browser.login(EMAIL, PASSWORD, true).andExpect(status().isNoContent());
        var response = browser.send(post("/api/auth/logout")).andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(header -> header.startsWith("SESSION=;") && header.contains("Max-Age=0"));
        assertThat(browser.sessionId()).isNull();
    }

    private Duration storedTimeout(BrowserSession browser) {
        return sessions.findById(browser.sessionId()).getMaxInactiveInterval();
    }
}
