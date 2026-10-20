package pl.macieserafin.hourforge.auth.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RememberMeIT extends ApiIntegrationTestSupport {
    private static final String EMAIL = "remember@example.com";
    private static final String PASSWORD = "Test-password-2026";

    @Autowired MockMvc mvc;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Value("${server.servlet.session.timeout}") java.time.Duration sessionTimeout;

    @BeforeEach
    void createUser() {
        var user = fixtures.user(EMAIL, "Remember");
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
    }

    @Test
    void aNormalSessionEndsAfterEightHoursOfInactivity() {
        assertThat(sessionTimeout).isEqualTo(java.time.Duration.ofHours(8));
    }

    @Test
    void rememberMeKeepsTheSessionForThirtyDaysWithAPersistentCookie() throws Exception {
        var session = new MockHttpSession();
        var response = mvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .param("email", EMAIL).param("password", PASSWORD).param("rememberMe", "true"))
                .andExpect(status().isNoContent()).andReturn().getResponse();

        assertThat(session.getMaxInactiveInterval()).isEqualTo(30 * 24 * 3600);
        var cookie = response.getHeaders("Set-Cookie").stream().filter(header -> header.startsWith("JSESSIONID=")).findFirst().orElseThrow();
        assertThat(cookie).contains("Max-Age=2592000").contains("HttpOnly").contains("SameSite=Lax").contains("Path=/")
                .contains("JSESSIONID=" + session.getId());
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk());
    }

    @Test
    void withoutRememberMeTheSessionKeepsTheDefaultAndGetsNoPersistentCookie() throws Exception {
        var session = new MockHttpSession();
        var response = mvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isNoContent()).andReturn().getResponse();

        assertThat(session.getMaxInactiveInterval()).isNotEqualTo(30 * 24 * 3600);
        assertThat(response.getHeaders("Set-Cookie")).noneMatch(header -> header.contains("Max-Age=2592000"));
    }

    @Test
    void onlyTheExactValueTrueTurnsItOn() throws Exception {
        for (var value : new String[] {"1", "yes", "TRUE", "on", ""}) {
            var session = new MockHttpSession();
            mvc.perform(post("/api/auth/login").session(session).with(csrf())
                            .param("email", EMAIL).param("password", PASSWORD).param("rememberMe", value))
                    .andExpect(status().isNoContent());
            assertThat(session.getMaxInactiveInterval()).isNotEqualTo(30 * 24 * 3600);
        }
    }

    @Test
    void aFailedLoginNeverExtendsAnything() throws Exception {
        var session = new MockHttpSession();
        var response = mvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .param("email", EMAIL).param("password", "wrong-password").param("rememberMe", "true"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertThat(response.getHeaders("Set-Cookie")).noneMatch(header -> header.contains("Max-Age=2592000"));
        assertThat(session.getMaxInactiveInterval()).isNotEqualTo(30 * 24 * 3600);
    }
}
