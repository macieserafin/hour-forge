package pl.macieserafin.hourforge.auth.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.macieserafin.hourforge.support.BrowserSession;
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
    @Autowired pl.macieserafin.hourforge.auth.AccountMailService accountMail;

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

    @ParameterizedTest
    @ValueSource(strings = {"vıctim-is@is.test", "victim-iſ@is.test"})
    void resettingASimilarUnicodeAddressCannotChangeTheAsciiAccount(String unicodeEmail) throws Exception {
        user.setEmail("victim-is@is.test");
        users.flush();
        request(unicodeEmail).andExpect(status().isNoContent());
        assertThat(mail.sent()).isEmpty();

        var distinct = fixtures.user(unicodeEmail, "Different mailbox");
        credentials.saveAndFlush(new UserCredential(distinct, encoder.encode(PASSWORD)));
        request(unicodeEmail).andExpect(status().isNoContent());
        assertThat(mail.last().to()).isEqualTo(unicodeEmail);
        confirm(mail.lastToken("reset-password"), NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(encoder.matches(PASSWORD, credentials.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(encoder.matches(NEW_PASSWORD, credentials.findById(distinct.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void confirmingSetsTheNewPasswordEndsEverySessionAndUsesUpTheLink() throws Exception {
        var session = signIn(PASSWORD);
        request(EMAIL).andExpect(status().isNoContent());
        var token = mail.lastToken("reset-password");

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());
        session.perform(get("/api/me")).andExpect(status().isUnauthorized());
        login(PASSWORD, new BrowserSession(mvc)).andExpect(status().isUnauthorized());
        login(NEW_PASSWORD, new BrowserSession(mvc)).andExpect(status().isNoContent());
        confirm(token, "Yet-another-password-2026").andExpect(status().isBadRequest());
        login(NEW_PASSWORD, new BrowserSession(mvc)).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"password-change", "sign-out-everywhere", "address-change"})
    void aLinkIssuedBeforeAnotherSecurityActionOnTheAccountNoLongerWorks(String action) throws Exception {
        var session = signIn(PASSWORD);
        request(EMAIL).andExpect(status().isNoContent());
        var token = mail.lastToken("reset-password");

        switch (action) {
            case "password-change" -> session.send(post("/api/account/password").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Another-password-2026\"}")).andExpect(status().isNoContent());
            case "sign-out-everywhere" -> session.send(post("/api/account/sign-out-everywhere")).andExpect(status().isNoContent());
            default -> {
                session.send(post("/api/account/email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"moved@example.com\",\"currentPassword\":\"" + PASSWORD + "\"}")).andExpect(status().isNoContent());
                mvc.perform(post("/api/auth/email-change/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mail.lastToken("confirm-email-change") + "\"}")).andExpect(status().isNoContent());
            }
        }
        var before = credentials.findById(user.getId()).orElseThrow().getPasswordHash();
        em.clear();

        confirm(token, NEW_PASSWORD).andExpect(status().isBadRequest());

        assertThat(credentials.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(before);
        limiterClear();
        request(action.equals("address-change") ? "moved@example.com" : EMAIL).andExpect(status().isNoContent());
        confirm(mail.lastToken("reset-password"), NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void aResetIssuedFromAnOutdatedAccountReadCannotResetTheRenamedAccount() throws Exception {
        var session = signIn(PASSWORD);
        session.send(post("/api/account/email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"moved@example.com\",\"currentPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        var addressToken = mail.lastToken("confirm-email-change");
        // A reset request can have read this user before a concurrent address change commits.
        // Keep that snapshot while the confirmation below loads and updates a separate entity.
        em.clear();
        mvc.perform(post("/api/auth/email-change/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + addressToken + "\"}")).andExpect(status().isNoContent());

        accountMail.sendPasswordReset(user);

        assertThat(mail.last().to()).isEqualTo(EMAIL);
        confirm(mail.lastToken("reset-password"), NEW_PASSWORD).andExpect(status().isBadRequest());
        new BrowserSession(mvc).login("moved@example.com", PASSWORD).andExpect(status().isNoContent());
        request("moved@example.com").andExpect(status().isNoContent());
        confirm(mail.lastToken("reset-password"), NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void aLinkWithoutACredentialVersionRequiresANewResetRequest() throws Exception {
        request(EMAIL).andExpect(status().isNoContent());
        var token = mail.lastToken("reset-password");
        jdbc.update("update email_tokens set credential_version = null where user_id = ?", user.getId());
        em.clear();

        confirm(token, NEW_PASSWORD).andExpect(status().isBadRequest());
        login(PASSWORD, new BrowserSession(mvc)).andExpect(status().isNoContent());
        request(EMAIL).andExpect(status().isNoContent());
        confirm(mail.lastToken("reset-password"), NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void resettingTheFlowOfAnUnconfirmedAccountAlsoConfirmsItsAddress() throws Exception {
        user.setEmailVerifiedAt(null);
        users.flush();
        login(PASSWORD, new BrowserSession(mvc)).andExpect(status().isForbidden());
        request(EMAIL).andExpect(status().isNoContent());
        confirm(mail.lastToken("reset-password"), NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
        login(NEW_PASSWORD, new BrowserSession(mvc)).andExpect(status().isNoContent());
    }

    @Test
    void anUnacceptablePasswordDoesNotUseUpTheLink() throws Exception {
        request(EMAIL).andExpect(status().isNoContent());
        var token = mail.lastToken("reset-password");
        confirm(token, "short").andExpect(status().isBadRequest());
        confirm(token, "              ").andExpect(status().isBadRequest());
        login(PASSWORD, new BrowserSession(mvc)).andExpect(status().isNoContent());
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
        login(PASSWORD, new BrowserSession(mvc)).andExpect(status().isNoContent());
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

    private BrowserSession signIn(String password) throws Exception {
        var session = new BrowserSession(mvc);
        login(password, session).andExpect(status().isNoContent());
        return session;
    }

    private ResultActions login(String password, BrowserSession session) throws Exception {
        return session.login(EMAIL, password);
    }

    @Autowired org.springframework.security.core.userdetails.UserDetailsService userDetails;

    @Test
    void anAccountWithoutAPasswordSetsItsFirstPasswordThroughTheResetAndCanThenSignInWithIt() throws Exception {
        var passwordless = pl.macieserafin.hourforge.support.PasswordlessAccounts.create(fixtures, credentials, "google-only@example.com", "Google only");
        var signedIn = pl.macieserafin.hourforge.support.PasswordlessAccounts.signedIn(userDetails, passwordless);
        request("google-only@example.com").andExpect(status().isNoContent());
        assertThat(mail.last().to()).isEqualTo("google-only@example.com");

        confirm(mail.lastToken("reset-password"), NEW_PASSWORD).andExpect(status().isNoContent());
        var credential = credentials.findById(passwordless.getId()).orElseThrow();
        assertThat(encoder.matches(NEW_PASSWORD, credential.getPasswordHash())).isTrue();
        // Like every reset, it ends the sessions that existed before.
        mvc.perform(get("/api/me").with(signedIn)).andExpect(status().isUnauthorized());
        var session = new BrowserSession(mvc);
        session.login("google-only@example.com", NEW_PASSWORD).andExpect(status().isNoContent());
        session.perform(get("/api/me")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.hasPassword").value(true));
    }
}
