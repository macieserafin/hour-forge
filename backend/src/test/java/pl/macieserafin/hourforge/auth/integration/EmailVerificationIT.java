package pl.macieserafin.hourforge.auth.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailVerificationIT extends ApiIntegrationTestSupport {
    private static final String EMAIL = "verify@example.com";
    private static final String PASSWORD = "Current-password-2026";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired pl.macieserafin.hourforge.security.RateLimiter limiter;

    private User user;

    @BeforeEach
    void createUser() {
        user = fixtures.user(EMAIL, "Verify Test");
        user.setEmailVerifiedAt(null);
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
    }

    @Test
    void registrationSendsOneConfirmationLinkAndStoresOnlyItsHash() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"New\",\"email\":\"new@example.com\",\"password\":\"Brand-new-password-2026\"}"))
                .andExpect(status().isCreated());
        assertThat(mail.sent()).hasSize(1);
        var message = mail.last();
        assertThat(message.to()).isEqualTo("new@example.com");
        var token = mail.lastToken("verify-email");
        assertThat(token).hasSize(43);
        var hash = jdbc.queryForObject("select token_hash from email_tokens where purpose = 'VERIFY_EMAIL'", String.class);
        assertThat(hash).hasSize(64).isNotEqualTo(token);
        assertThat(users.findByEmailIgnoreCase("new@example.com").orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void confirmingMarksTheAddressVerifiedAndTheLinkWorksOnlyOnce() throws Exception {
        send(user);
        var token = mail.lastToken("verify-email");
        confirm(token).andExpect(status().isNoContent());
        assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
        confirm(token).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-a-token", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void unknownTokensAreRejected(String token) throws Exception {
        confirm(token).andExpect(status().isBadRequest());
    }

    @Test
    void expiredAndWrongPurposeLinksAreRejected() throws Exception {
        send(user);
        var token = mail.lastToken("verify-email");
        jdbc.update("update email_tokens set expires_at = now() - interval '1 minute'"); em.flush(); em.clear();
        confirm(token).andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();

        send(user);
        var fresh = mail.lastToken("verify-email");
        jdbc.update("update email_tokens set purpose = 'RESET_PASSWORD' where used_at is null"); em.flush(); em.clear();
        confirm(fresh).andExpect(status().isBadRequest());
    }

    @Test
    void aNewLinkReplacesTheOldOne() throws Exception {
        send(user);
        var first = mail.lastToken("verify-email");
        send(user);
        var second = mail.lastToken("verify-email");
        assertThat(second).isNotEqualTo(first);
        confirm(first).andExpect(status().isBadRequest());
        confirm(second).andExpect(status().isNoContent());
    }

    @Test
    void publicResendLooksTheSameForEveryAddressAndOnlySendsToUnverifiedAccounts() throws Exception {
        resend(EMAIL.toUpperCase()).andExpect(status().isNoContent());
        assertThat(mail.sent()).hasSize(1);
        assertThat(mail.last().to()).isEqualTo(EMAIL);

        resend("nobody@example.com").andExpect(status().isNoContent());
        resend("").andExpect(status().isNoContent());
        assertThat(mail.sent()).hasSize(1);

        user.setEmailVerifiedAt(java.time.Instant.now());
        users.flush();
        resend(EMAIL).andExpect(status().isNoContent());
        assertThat(mail.sent()).hasSize(1);
    }

    @Test
    void publicResendSilentlyStopsSendingAfterThePerAddressLimitAndBlocksAnIpThatSpams() throws Exception {
        for (int i = 0; i < 5; i++) resend(EMAIL).andExpect(status().isNoContent());
        assertThat(mail.sent()).hasSize(3);
        for (int i = 5; i < 10; i++) resend("other" + i + "@example.com").andExpect(status().isNoContent());
        resend("another@example.com").andExpect(status().isTooManyRequests());
    }

    @Test
    void endpointsRequireCsrfButNotAuthentication() throws Exception {
        mvc.perform(post("/api/auth/email-verification/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\"}")).andExpect(status().isForbidden());
    }

    // Goes through the public flow, exactly as a user would request a new link.
    private void send(User target) throws Exception {
        resend(target.getEmail()).andExpect(status().isNoContent());
        limiter.clear();
    }

    private ResultActions confirm(String token) throws Exception {
        return mvc.perform(post("/api/auth/email-verification/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"));
    }

    private ResultActions resend(String email) throws Exception {
        return mvc.perform(post("/api/auth/email-verification/resend").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"));
    }

}
