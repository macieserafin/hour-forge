package pl.macieserafin.hourforge.account.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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

class EmailChangeIT extends ApiIntegrationTestSupport {
    private static final String EMAIL = "change@example.com";
    private static final String NEW_EMAIL = "renamed@example.com";
    private static final String PASSWORD = "Current-password-2026";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;

    private User user;

    @BeforeEach
    void createUser() {
        user = fixtures.user(EMAIL, "Change Test");
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
    }

    @Test
    void requestSendsTheLinkToTheNewAddressOnlyAndChangesNothingYet() throws Exception {
        var session = signIn(EMAIL);
        change(session, "  Renamed@Example.com ", PASSWORD).andExpect(status().isNoContent());
        assertThat(mail.sent()).hasSize(1);
        assertThat(mail.last().to()).isEqualTo(NEW_EMAIL);
        assertThat(mail.last().body()).contains("/confirm-email-change/");
        assertThat(users.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(EMAIL);
        session.perform(get("/api/me")).andExpect(status().isOk());
    }

    @Test
    void confirmingSwitchesTheSignInAddressEndsSessionsAndNotifiesTheOldAddress() throws Exception {
        var session = signIn(EMAIL);
        change(session, NEW_EMAIL, PASSWORD).andExpect(status().isNoContent());
        var token = mail.lastToken("confirm-email-change");
        mail.clear();

        confirm(token).andExpect(status().isNoContent());
        var changed = users.findById(user.getId()).orElseThrow();
        assertThat(changed.getEmail()).isEqualTo(NEW_EMAIL);
        assertThat(changed.isEmailVerified()).isTrue();
        session.perform(get("/api/me")).andExpect(status().isUnauthorized());
        login(EMAIL, PASSWORD, new BrowserSession(mvc)).andExpect(status().isUnauthorized());
        login(NEW_EMAIL, PASSWORD, new BrowserSession(mvc)).andExpect(status().isNoContent());
        assertThat(mail.sent()).hasSize(1);
        assertThat(mail.last().to()).isEqualTo(EMAIL);
        assertThat(mail.last().body()).contains(NEW_EMAIL);
        confirm(token).andExpect(status().isBadRequest());
    }

    @Test
    void anAddressUsedByAnotherAccountLooksTheSameButGetsNoLinkAndCannotBeTakenLater() throws Exception {
        fixtures.user(NEW_EMAIL, "Someone else");
        var session = signIn(EMAIL);
        change(session, NEW_EMAIL.toUpperCase(), PASSWORD).andExpect(status().isNoContent());
        assertThat(mail.sent()).isEmpty();

        change(session, "free@example.com", PASSWORD).andExpect(status().isNoContent());
        var token = mail.lastToken("confirm-email-change");
        // The address is taken between the request and the click.
        fixtures.user("free@example.com", "Quick one");
        confirm(token).andExpect(status().isConflict());
        assertThat(users.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void theCurrentPasswordIsRequiredAndWrongAnswersAreLimited() throws Exception {
        var session = signIn(EMAIL);
        for (int i = 0; i < 5; i++) change(session, NEW_EMAIL, "wrong-password-value").andExpect(status().isBadRequest());
        change(session, NEW_EMAIL, PASSWORD).andExpect(status().isTooManyRequests());
        assertThat(mail.sent()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-an-email", "change@example.com", "CHANGE@example.com"})
    void invalidOrUnchangedAddressesAreRejected(String address) throws Exception {
        change(signIn(EMAIL), address, PASSWORD).andExpect(status().isBadRequest());
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void requestsAreLimitedPerAccount() throws Exception {
        var session = signIn(EMAIL);
        for (int i = 0; i < 3; i++) change(session, "new" + i + "@example.com", PASSWORD).andExpect(status().isNoContent());
        change(session, "new4@example.com", PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void unknownAndForeignLinksAreRejectedAndEndpointsNeedTheRightAuthentication() throws Exception {
        confirm("not-a-token").andExpect(status().isBadRequest());
        mvc.perform(post("/api/account/email").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"" + NEW_EMAIL + "\",\"currentPassword\":\"" + PASSWORD + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions change(BrowserSession session, String address, String password) throws Exception {
        return session.send(post("/api/account/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"" + address + "\",\"currentPassword\":\"" + password + "\"}"));
    }

    private ResultActions confirm(String token) throws Exception {
        return mvc.perform(post("/api/auth/email-change/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private BrowserSession signIn(String email) throws Exception {
        var session = new BrowserSession(mvc);
        login(email, PASSWORD, session).andExpect(status().isNoContent());
        return session;
    }

    private ResultActions login(String email, String password, BrowserSession session) throws Exception {
        return session.login(email, password);
    }
}
