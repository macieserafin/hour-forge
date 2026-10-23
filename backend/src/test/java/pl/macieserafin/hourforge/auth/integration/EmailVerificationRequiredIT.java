package pl.macieserafin.hourforge.auth.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pl.macieserafin.hourforge.support.BrowserSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailVerificationRequiredIT extends ApiIntegrationTestSupport {
    private static final String EMAIL = "required@example.com";
    private static final String PASSWORD = "Current-password-2026";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void createUser() {
        var user = fixtures.user(EMAIL, "Required Test");
        user.setEmailVerifiedAt(null);
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
    }

    @Test
    void unconfirmedAccountsCannotSignInUntilTheyConfirmTheirAddress() throws Exception {
        var session = new BrowserSession(mvc);
        login(PASSWORD, session).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        session.perform(get("/api/me")).andExpect(status().isUnauthorized());
        // A wrong password still looks like any failed login, so the state is not revealed to strangers.
        login("wrong-password-value", new BrowserSession(mvc)).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/email-verification/resend").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/email-verification/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + mail.lastToken("verify-email") + "\"}")).andExpect(status().isNoContent());

        var confirmed = new BrowserSession(mvc);
        login(PASSWORD, confirmed).andExpect(status().isNoContent());
        confirmed.perform(get("/api/me")).andExpect(status().isOk());
    }

    private ResultActions login(String password, BrowserSession session) throws Exception {
        return session.login(EMAIL, password);
    }
}
