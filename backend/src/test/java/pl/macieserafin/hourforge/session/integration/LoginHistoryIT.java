package pl.macieserafin.hourforge.session.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.support.BrowserSession;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginHistoryIT extends ApiIntegrationTestSupport {
    private static final String EMAIL = "history@example.com";
    private static final String PASSWORD = "Test-password-2026";
    private static final String FIREFOX_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0";
    private static final String CHROME_ANDROID = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
    private static final String NEW_DEVICE_SUBJECT = "Nowe logowanie na Twoje konto HourForge";

    @Autowired MockMvc mvc;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    private User user;

    @BeforeEach
    void createUser() {
        user = fixtures.user(EMAIL, "History");
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
    }

    @Test
    void showsTheOwnSignInsNewestFirstWithOnlyAShortenedAddress() throws Exception {
        browser(FIREFOX_WINDOWS, "83.24.117.9").login(EMAIL, "wrong-password-value").andExpect(status().isUnauthorized());
        var signedIn = browser(FIREFOX_WINDOWS, "83.24.117.9");
        signedIn.login(EMAIL, PASSWORD).andExpect(status().isNoContent());

        var body = signedIn.perform(get("/api/account/logins")).andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(2)))
                .andExpect(jsonPath("$.entries[0].successful").value(true))
                .andExpect(jsonPath("$.entries[0].browser").value("Firefox"))
                .andExpect(jsonPath("$.entries[0].system").value("Windows"))
                .andExpect(jsonPath("$.entries[0].address").value("83.24.x.x"))
                .andExpect(jsonPath("$.entries[1].successful").value(false))
                // Without a location database no places are shown, and so no credit either.
                .andExpect(jsonPath("$.entries[0].location").value(nullValue()))
                .andExpect(jsonPath("$.locationSource").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("117.9");
    }

    @Test
    void attemptsOnUnknownAddressesAreNotStoredAndOthersDoNotSeeMine() throws Exception {
        browser(FIREFOX_WINDOWS, "10.0.0.1").login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized());
        browser(FIREFOX_WINDOWS, "10.0.0.1").login(EMAIL, PASSWORD).andExpect(status().isNoContent());
        var other = fixtures.user("other-history@example.com", "Other");
        credentials.saveAndFlush(new UserCredential(other, encoder.encode(PASSWORD)));
        var theirs = browser(FIREFOX_WINDOWS, "10.0.0.2");
        theirs.login("other-history@example.com", PASSWORD).andExpect(status().isNoContent());

        theirs.perform(get("/api/account/logins")).andExpect(jsonPath("$.entries", hasSize(1)));
        assertThat(jdbc.queryForObject("select count(*) from login_events where user_id = ?", Integer.class, user.getId())).isEqualTo(1);
        mvc.perform(get("/api/account/logins")).andExpect(status().isUnauthorized());
    }

    @Test
    void mailsThePersonOnlyWhenAKnownAccountIsUsedFromANewKindOfDevice() throws Exception {
        browser(FIREFOX_WINDOWS, "83.24.117.9").login(EMAIL, PASSWORD).andExpect(status().isNoContent());
        browser(FIREFOX_WINDOWS, "91.1.2.3").login(EMAIL, PASSWORD).andExpect(status().isNoContent());
        browser(CHROME_ANDROID, "91.1.2.3").login(EMAIL, "wrong-password-value").andExpect(status().isUnauthorized());
        // The first sign-in of an account, the same device from elsewhere and a failed attempt are not news.
        assertThat(mail.sent()).noneMatch(message -> message.subject().equals(NEW_DEVICE_SUBJECT));

        browser(CHROME_ANDROID, "91.1.2.3").login(EMAIL, PASSWORD).andExpect(status().isNoContent());

        var message = mail.last();
        assertThat(message.to()).isEqualTo(EMAIL);
        assertThat(message.subject()).isEqualTo(NEW_DEVICE_SUBJECT);
        assertThat(message.body()).contains("Urządzenie: Chrome · Android").contains("Adres: 91.1.x.x")
                .contains("/account#sessions").doesNotContain("91.1.2.3");
        mail.clear();
        browser(CHROME_ANDROID, "91.1.2.4").login(EMAIL, PASSWORD).andExpect(status().isNoContent());
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void theExportCarriesTheSignInsAndDeletingTheAccountRemovesThem() throws Exception {
        var signedIn = browser(FIREFOX_WINDOWS, "83.24.117.9");
        signedIn.login(EMAIL, PASSWORD).andExpect(status().isNoContent());

        signedIn.perform(get("/api/account/export")).andExpect(status().isOk())
                .andExpect(jsonPath("$.logins", hasSize(1)))
                .andExpect(jsonPath("$.logins[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.logins[0].address").value("83.24.x.x"));

        signedIn.send(post("/api/account/delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from login_events where user_id = ?", Integer.class, user.getId())).isZero();
    }

    private BrowserSession browser(String userAgent, String address) {
        return new BrowserSession(mvc).device(userAgent, address);
    }
}
