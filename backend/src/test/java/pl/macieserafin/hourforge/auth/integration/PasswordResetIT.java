package pl.macieserafin.hourforge.auth.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetIT extends ApiIntegrationTestSupport {
    private static final String EMAIL = "reset@example.com";
    private static final String PASSWORD = "Current-password-2026";
    private static final String NEW_PASSWORD = "Brand-new-password-2026";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    private User user;

    @BeforeEach
    void createUser() {
        user = fixtures.user(EMAIL, "Reset Test");
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
    }

    @Test
    void requestSendsOneLinkToAKnownAddressAndLooksTheSameForAnUnknownOne() throws Exception {
        request(EMAIL.toUpperCase()).andExpect(status().isNoContent());
        assertThat(mail.sent()).hasSize(1);
        assertThat(mail.last().to()).isEqualTo(EMAIL);
        assertThat(mail.last().body()).contains("/reset-password/");
        request("nobody@example.com").andExpect(status().isNoContent());
        request("").andExpect(status().isNoContent());
        assertThat(mail.sent()).hasSize(1);
    }

    @Test
    void confirmingSetsTheNewPasswordEndsEverySessionAndUsesUpTheLink() throws Exception {
        var session = signIn(PASSWORD);
        request(EMAIL).andExpect(status().isNoContent());
        var token = mail.lastToken("reset-password");

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
        login(PASSWORD, new MockHttpSession()).andExpect(status().isUnauthorized());
        login(NEW_PASSWORD, new MockHttpSession()).andExpect(status().isNoContent());
        assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
        confirm(token, "Yet-another-password-2026").andExpect(status().isBadRequest());
        login(NEW_PASSWORD, new MockHttpSession()).andExpect(status().isNoContent());
    }

    @Test
    void anUnacceptablePasswordDoesNotUseUpTheLink() throws Exception {
        request(EMAIL).andExpect(status().isNoContent());
        var token = mail.lastToken("reset-password");
        confirm(token, "short").andExpect(status().isBadRequest());
        confirm(token, "              ").andExpect(status().isBadRequest());
        login(PASSWORD, new MockHttpSession()).andExpect(status().isNoContent());
        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void unknownExpiredAndForeignPurposeLinksAreRejected() throws Exception {
        confirm("not-a-token", NEW_PASSWORD).andExpect(status().isBadRequest());
        confirm("", NEW_PASSWORD).andExpect(status().isBadRequest());

        request(EMAIL).andExpect(status().isNoContent());
        var token = mail.lastToken("reset-password");
        jdbc.update("update email_tokens set expires_at = now() - interval '1 minute'");
        em.flush();
        em.clear();
        confirm(token, NEW_PASSWORD).andExpect(status().isBadRequest());

        limiterClear();
        request(EMAIL).andExpect(status().isNoContent());
        var other = mail.lastToken("reset-password");
        jdbc.update("update email_tokens set purpose = 'VERIFY_EMAIL' where used_at is null");
        em.flush();
        em.clear();
        confirm(other, NEW_PASSWORD).andExpect(status().isBadRequest());
        login(PASSWORD, new MockHttpSession()).andExpect(status().isNoContent());
    }

    @Test
    void aNewRequestReplacesTheEarlierLink() throws Exception {
        request(EMAIL).andExpect(status().isNoContent());
        var first = mail.lastToken("reset-password");
        request(EMAIL).andExpect(status().isNoContent());
        var second = mail.lastToken("reset-password");
        confirm(first, NEW_PASSWORD).andExpect(status().isBadRequest());
        confirm(second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void requestsAreLimitedPerAccountSilentlyAndPerIpWithAnError() throws Exception {
        for (int i = 0; i < 5; i++) request(EMAIL).andExpect(status().isNoContent());
        assertThat(mail.sent()).hasSize(3);
        for (int i = 5; i < 10; i++) request("other" + i + "@example.com").andExpect(status().isNoContent());
        request("another@example.com").andExpect(status().isTooManyRequests());
    }

    @Test
    void endpointsNeedCsrfButNoAuthentication() throws Exception {
        mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"x\",\"newPassword\":\"" + NEW_PASSWORD + "\"}")).andExpect(status().isForbidden());
    }

    @Autowired pl.macieserafin.hourforge.security.RateLimiter limiter;

    private void limiterClear() {
        limiter.clear();
    }

    private ResultActions request(String email) throws Exception {
        return mvc.perform(post("/api/auth/password-reset/request").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions confirm(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/password-reset/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"));
    }

    private MockHttpSession signIn(String password) throws Exception {
        var session = new MockHttpSession();
        login(password, session).andExpect(status().isNoContent());
        return session;
    }

    private ResultActions login(String password, MockHttpSession session) throws Exception {
        var token = JsonPath.<String>read(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString(), "$.token");
        return mvc.perform(post("/api/auth/login").session(session).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-CSRF-TOKEN", token).param("email", EMAIL).param("password", password));
    }
}
