package pl.macieserafin.hourforge.session.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.support.BrowserSession;
import pl.macieserafin.hourforge.user.User;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceSessionsIT extends ApiIntegrationTestSupport {
    private static final String EMAIL = "devices@example.com";
    private static final String OTHER_EMAIL = "someone-else@example.com";
    private static final String PASSWORD = "Test-password-2026";
    private static final String FIREFOX_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0";
    private static final String SAFARI_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";

    @Autowired MockMvc mvc;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired FindByIndexNameSessionRepository<? extends Session> sessions;
    @Autowired JdbcTemplate jdbc;

    private User user;

    @BeforeEach
    void createUsers() {
        user = fixtures.user(EMAIL, "Devices");
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
        var other = fixtures.user(OTHER_EMAIL, "Other");
        credentials.saveAndFlush(new UserCredential(other, encoder.encode(PASSWORD)));
    }

    @Test
    void listsEachSignedInDeviceWithTheCurrentOneFirstAndOnlyAShortenedAddress() throws Exception {
        var laptop = signIn(FIREFOX_WINDOWS, "83.24.117.9", false);
        signIn(SAFARI_IPHONE, "2a02:a311:1234:5678::1", true);

        var body = laptop.perform(get("/api/account/sessions")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].browser").value("Firefox"))
                .andExpect(jsonPath("$[0].system").value("Windows"))
                .andExpect(jsonPath("$[0].address").value("83.24.x.x"))
                .andExpect(jsonPath("$[0].remembered").value(false))
                .andExpect(jsonPath("$[1].current").value(false))
                .andExpect(jsonPath("$[1].browser").value("Safari"))
                .andExpect(jsonPath("$[1].system").value("iOS"))
                .andExpect(jsonPath("$[1].address").value("2a02:a311:x:x::"))
                .andExpect(jsonPath("$[1].remembered").value(true))
                .andExpect(jsonPath("$[1].signedInAt").isNotEmpty())
                .andExpect(jsonPath("$[1].lastActiveAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // The handle is not the session id, which is the cookie value.
        assertThat(body).doesNotContain(laptop.sessionId()).doesNotContain("83.24.117.9").doesNotContain("117.9");
    }

    @Test
    void endingAnotherDeviceSignsItOutAndRemovesItFromTheList() throws Exception {
        var laptop = signIn(FIREFOX_WINDOWS, "83.24.117.9", false);
        var phone = signIn(SAFARI_IPHONE, "83.24.117.10", false);
        var phoneHandle = handles(laptop).get(1);

        laptop.send(delete("/api/account/sessions/" + phoneHandle)).andExpect(status().isNoContent());

        phone.perform(get("/api/me")).andExpect(status().isUnauthorized());
        laptop.perform(get("/api/account/sessions")).andExpect(jsonPath("$", hasSize(1)));
        laptop.perform(get("/api/me")).andExpect(status().isOk());
    }

    @Test
    void theCurrentSessionIsEndedBySigningOutNotFromTheList() throws Exception {
        var laptop = signIn(FIREFOX_WINDOWS, "83.24.117.9", false);
        laptop.send(delete("/api/account/sessions/" + handles(laptop).get(0))).andExpect(status().isBadRequest());
        laptop.perform(get("/api/me")).andExpect(status().isOk());
    }

    @Test
    void aPersonCannotSeeOrEndTheSessionsOfSomeoneElse() throws Exception {
        var mine = signIn(FIREFOX_WINDOWS, "83.24.117.9", false);
        var theirs = new BrowserSession(mvc).device(SAFARI_IPHONE, "10.1.2.3");
        theirs.login(OTHER_EMAIL, PASSWORD).andExpect(status().isNoContent());
        var theirHandle = handles(theirs).get(0);

        mine.perform(get("/api/account/sessions")).andExpect(jsonPath("$", hasSize(1)));
        mine.send(delete("/api/account/sessions/" + theirHandle)).andExpect(status().isNotFound());
        mine.send(delete("/api/account/sessions/not-a-handle")).andExpect(status().isNotFound());
        theirs.perform(get("/api/me")).andExpect(status().isOk());
    }

    @Test
    void theListNeedsASignedInPersonAndEndingNeedsCsrf() throws Exception {
        mvc.perform(get("/api/account/sessions")).andExpect(status().isUnauthorized());
        var laptop = signIn(FIREFOX_WINDOWS, "83.24.117.9", false);
        var phone = signIn(SAFARI_IPHONE, "83.24.117.10", false);
        laptop.perform(delete("/api/account/sessions/" + handles(laptop).get(1))).andExpect(status().isForbidden());
        phone.perform(get("/api/me")).andExpect(status().isOk());
    }

    @Test
    void changingThePasswordRemovesTheOtherDevicesButKeepsThisOne() throws Exception {
        var laptop = signIn(FIREFOX_WINDOWS, "83.24.117.9", false);
        var phone = signIn(SAFARI_IPHONE, "83.24.117.10", false);

        laptop.send(post("/api/account/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Another-password-2026\"}"))
                .andExpect(status().isNoContent());

        phone.perform(get("/api/me")).andExpect(status().isUnauthorized());
        laptop.perform(get("/api/account/sessions")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void signingOutEverywhereRemovesEveryStoredSession() throws Exception {
        var laptop = signIn(FIREFOX_WINDOWS, "83.24.117.9", false);
        signIn(SAFARI_IPHONE, "83.24.117.10", false);

        laptop.send(post("/api/account/sign-out-everywhere")).andExpect(status().isNoContent());

        assertThat(sessions.findByPrincipalName(user.getId().toString())).isEmpty();
    }

    @Test
    void usingTheAppRecordsTheDayOfLastActivity() throws Exception {
        jdbc.update("update users set last_active_at = null where id = ?", user.getId());
        var laptop = signIn(FIREFOX_WINDOWS, "83.24.117.9", false);
        laptop.perform(get("/api/me")).andExpect(status().isOk());

        var recorded = jdbc.queryForObject("select last_active_at from users where id = ?", Timestamp.class, user.getId()).toInstant();
        assertThat(recorded).isEqualTo(Instant.now().truncatedTo(ChronoUnit.DAYS));
    }

    @Test
    void storedSessionsAreIndexedByTheUserIdSoAnEmailChangeKeepsThemListed() throws Exception {
        signIn(FIREFOX_WINDOWS, "83.24.117.9", false);
        assertThat(sessions.findByPrincipalName(user.getId().toString())).hasSize(1);
        assertThat(sessions.findByPrincipalName(EMAIL)).isEmpty();
    }

    private BrowserSession signIn(String userAgent, String address, boolean rememberMe) throws Exception {
        var browser = new BrowserSession(mvc).device(userAgent, address);
        browser.login(EMAIL, PASSWORD, rememberMe).andExpect(status().isNoContent());
        return browser;
    }

    private List<String> handles(BrowserSession browser) throws Exception {
        return JsonPath.read(browser.perform(get("/api/account/sessions")).andReturn().getResponse().getContentAsString(), "$[*].id");
    }
}
