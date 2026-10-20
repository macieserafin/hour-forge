package pl.macieserafin.hourforge.account.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountIT extends ApiIntegrationTestSupport {
    private static final String EMAIL = "account@example.com";
    private static final String PASSWORD = "Current-password-2026";
    private static final String NEW_PASSWORD = "Brand-new-password-2026";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;

    private User user;

    @BeforeEach
    void createUser() {
        user = fixtures.user(EMAIL, "Account Test");
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
    }

    @Test
    void profileUpdateTrimsAndPersistsDisplayName() throws Exception {
        var session = signIn(PASSWORD);
        send(patch("/api/account/profile"), session, "{\"displayName\":\"  New Name  \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("New Name"))
                .andExpect(jsonPath("$.email").value(EMAIL));
        assertThat(users.findById(user.getId()).orElseThrow().getDisplayName()).isEqualTo("New Name");
        mvc.perform(get("/api/me").session(session)).andExpect(jsonPath("$.displayName").value("New Name"));
        send(patch("/api/account/profile"), session, "{\"displayName\":\"" + "x".repeat(255) + "\"}").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"displayName\":\"   \"}", "{\"displayName\":null}"})
    void profileUpdateRejectsBlankName(String body) throws Exception {
        send(patch("/api/account/profile"), signIn(PASSWORD), body).andExpect(status().isBadRequest());
        assertThat(users.findById(user.getId()).orElseThrow().getDisplayName()).isEqualTo("Account Test");
    }

    @Test
    void profileUpdateRejectsTooLongName() throws Exception {
        send(patch("/api/account/profile"), signIn(PASSWORD), "{\"displayName\":\"" + "x".repeat(256) + "\"}")
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/account/profile", "/api/account/password", "/api/account/sign-out-everywhere"})
    void accountEndpointsRequireAuthenticationAndCsrf(String path) throws Exception {
        var request = path.endsWith("profile") ? patch(path) : post(path);
        mvc.perform(request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        var session = signIn(PASSWORD);
        mvc.perform((path.endsWith("profile") ? patch(path) : post(path)).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordChangeEndsOtherSessionsButKeepsTheCurrentOne() throws Exception {
        var current = signIn(PASSWORD);
        var other = signIn(PASSWORD);
        changePassword(current, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent()).andExpect(content().string(""));

        mvc.perform(get("/api/me").session(current)).andExpect(status().isOk());
        mvc.perform(get("/api/me").session(other)).andExpect(status().isUnauthorized());
        assertThat(other.isInvalid()).isTrue();
        login(PASSWORD, new MockHttpSession()).andExpect(status().isUnauthorized());
        login(NEW_PASSWORD, new MockHttpSession()).andExpect(status().isNoContent());
        assertThat(encoder.matches(NEW_PASSWORD, credentials.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void currentSessionKeepsWorkingAcrossRepeatedChanges() throws Exception {
        var current = signIn(PASSWORD);
        changePassword(current, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
        changePassword(current, NEW_PASSWORD, "Another-password-2027").andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(current)).andExpect(status().isOk());
        assertThat(credentials.findById(user.getId()).orElseThrow().getSessionVersion()).isEqualTo(2);
    }

    @Test
    void wrongCurrentPasswordIsRejectedWithoutChangesAndEventuallyBlocked() throws Exception {
        var session = signIn(PASSWORD);
        var hash = credentials.findById(user.getId()).orElseThrow().getPasswordHash();
        for (int attempt = 0; attempt < 5; attempt++) {
            changePassword(session, "wrong-password-value", NEW_PASSWORD).andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("wrong-password-value"))));
        }
        assertThat(credentials.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
        changePassword(session, PASSWORD, NEW_PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertThat(credentials.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "                  ", "Current-password-2026", "ąąąąąąąąąąąąąąąąąąąąąąąąąąąąąąąąąąąąą"})
    void unacceptableNewPasswordsAreRejected(String newPassword) throws Exception {
        var session = signIn(PASSWORD);
        changePassword(session, PASSWORD, newPassword).andExpect(status().isBadRequest());
        login(PASSWORD, new MockHttpSession()).andExpect(status().isNoContent());
    }

    @Test
    void missingPasswordsAreRejected() throws Exception {
        var session = signIn(PASSWORD);
        send(post("/api/account/password"), session, "{}").andExpect(status().isBadRequest());
        send(post("/api/account/password"), session, "{\"currentPassword\":\"" + PASSWORD + "\"}").andExpect(status().isBadRequest());
        send(post("/api/account/password"), session, "{\"newPassword\":\"" + NEW_PASSWORD + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void signingOutEverywhereEndsEverySessionIncludingTheCurrentOne() throws Exception {
        var current = signIn(PASSWORD);
        var other = signIn(PASSWORD);
        send(post("/api/account/sign-out-everywhere"), current, null).andExpect(status().isNoContent());
        assertThat(current.isInvalid()).isTrue();
        mvc.perform(get("/api/me").session(other)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").session(current)).andExpect(status().isUnauthorized());
        login(PASSWORD, new MockHttpSession()).andExpect(status().isNoContent());
    }

    @Test
    void sessionsOfAnAccountWithoutCredentialsAreRejected() throws Exception {
        var session = signIn(PASSWORD);
        credentials.delete(credentials.findById(user.getId()).orElseThrow());
        credentials.flush();
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
    }

    private MockHttpSession signIn(String password) throws Exception {
        var session = new MockHttpSession();
        login(password, session).andExpect(status().isNoContent());
        return session;
    }

    private ResultActions login(String password, MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/auth/login").session(session).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-CSRF-TOKEN", csrfToken(session)).param("email", EMAIL).param("password", password));
    }

    private ResultActions changePassword(MockHttpSession session, String current, String next) throws Exception {
        return send(post("/api/account/password"), session,
                "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}");
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                               MockHttpSession session, String body) throws Exception {
        builder.session(session).header("X-CSRF-TOKEN", csrfToken(session));
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(builder);
    }

    private String csrfToken(MockHttpSession session) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.token");
    }
}
