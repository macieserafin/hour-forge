package pl.macieserafin.hourforge.twofactor.integration;

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
import org.springframework.test.web.servlet.ResultActions;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.support.BrowserSession;
import pl.macieserafin.hourforge.twofactor.Totp;
import pl.macieserafin.hourforge.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TwoFactorIT extends ApiIntegrationTestSupport {
    private static final String EMAIL = "two-step@example.com";
    private static final String PASSWORD = "Test-password-2026";

    @Autowired MockMvc mvc;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired FindByIndexNameSessionRepository<? extends Session> sessions;

    private User user;
    private BrowserSession owner;
    private String secret;

    @BeforeEach
    void signIn() throws Exception {
        user = fixtures.user(EMAIL, "Two Step");
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
        owner = new BrowserSession(mvc);
        owner.login(EMAIL, PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void settingUpNeedsAValidCodeAndThePasswordAndGivesTenOneTimeBackupCodes() throws Exception {
        owner.perform(get("/api/account/two-factor")).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.enabled").value(false));
        var setup = owner.send(post("/api/account/two-factor/setup")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.uri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/HourForge:two-step%40example.com?secret=")))
                .andReturn().getResponse().getContentAsString();
        secret = JsonPath.read(setup, "$.secret");

        enable("000000", PASSWORD).andExpect(status().isBadRequest());
        enable(codeAt(0), "wrong-password-value").andExpect(status().isBadRequest());
        owner.perform(get("/api/account/two-factor")).andExpect(jsonPath("$.enabled").value(false));
        assertThat(jdbc.queryForObject("select secret from user_two_factor where user_id = ?", String.class, user.getId()))
                .doesNotContain(secret).startsWith("v1:");

        enable(codeAt(0), PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.codes", hasSize(10)))
                .andExpect(jsonPath("$.codes[0]").value(org.hamcrest.Matchers.matchesPattern("[a-z2-9]{5}-[a-z2-9]{5}")));
        owner.perform(get("/api/account/two-factor"))
                .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.backupCodesLeft").value(10));
        assertThat(mail.last().subject()).isEqualTo("Włączono logowanie dwuetapowe w HourForge");
        owner.send(post("/api/account/two-factor/setup")).andExpect(status().isConflict());
    }

    @Test
    void aRightPasswordAloneNoLongerSignsInUntilTheCodeIsGiven() throws Exception {
        turnOn();
        var laptop = new BrowserSession(mvc);
        laptop.login(EMAIL, PASSWORD).andExpect(status().isAccepted()).andExpect(jsonPath("$.code").value("TWO_FACTOR_REQUIRED"));
        laptop.perform(get("/api/me")).andExpect(status().isUnauthorized());
        var waiting = laptop.sessionId();

        answer(laptop, codeAt(1)).andExpect(status().isNoContent());

        assertThat(laptop.sessionId()).isNotEqualTo(waiting);
        laptop.perform(get("/api/me")).andExpect(status().isOk());
        laptop.perform(get("/api/account/sessions")).andExpect(jsonPath("$", hasSize(2)));
        laptop.perform(get("/api/account/logins")).andExpect(jsonPath("$.entries[0].successful").value(true));
    }

    @Test
    void aCodeWorksOnceAndABackupCodeWorksOnce() throws Exception {
        var codes = turnOn();
        var first = new BrowserSession(mvc);
        first.login(EMAIL, PASSWORD).andExpect(status().isAccepted());
        answer(first, codeAt(1)).andExpect(status().isNoContent());

        var second = new BrowserSession(mvc);
        second.login(EMAIL, PASSWORD).andExpect(status().isAccepted());
        answer(second, codeAt(1)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CODE"));
        answer(second, codes.get(0).toUpperCase()).andExpect(status().isNoContent());

        var third = new BrowserSession(mvc);
        third.login(EMAIL, PASSWORD).andExpect(status().isAccepted());
        answer(third, codes.get(0)).andExpect(status().isBadRequest());
        owner.perform(get("/api/account/two-factor")).andExpect(jsonPath("$.backupCodesLeft").value(9));
    }

    @Test
    void wrongCodesEndTheAttemptAndThenBlockThePersonForAWhile() throws Exception {
        turnOn();
        var guesser = new BrowserSession(mvc);
        guesser.login(EMAIL, PASSWORD).andExpect(status().isAccepted());
        for (int i = 0; i < 4; i++) answer(guesser, "11111" + i).andExpect(status().isBadRequest());
        answer(guesser, "111115").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SIGN_IN_AGAIN"));
        guesser.perform(get("/api/me")).andExpect(status().isUnauthorized());

        var again = new BrowserSession(mvc);
        again.login(EMAIL, PASSWORD).andExpect(status().isAccepted());
        answer(again, codeAt(1)).andExpect(status().isTooManyRequests());
        entityManager.flush();
        assertThat(jdbc.queryForObject("select count(*) from login_events where user_id = ? and outcome = 'FAILED'", Integer.class, user.getId()))
                .isEqualTo(5);
    }

    @Test
    void theSecondStepNeedsAWaitingSignInAndCsrf() throws Exception {
        turnOn();
        answer(new BrowserSession(mvc), codeAt(1)).andExpect(status().isUnauthorized());
        var laptop = new BrowserSession(mvc);
        laptop.login(EMAIL, PASSWORD).andExpect(status().isAccepted());
        laptop.perform(post("/api/auth/two-factor").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + codeAt(1) + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rememberMeCarriesOverTheSecondStep() throws Exception {
        turnOn();
        var laptop = new BrowserSession(mvc);
        laptop.login(EMAIL, PASSWORD, true).andExpect(status().isAccepted());
        var response = answer(laptop, codeAt(1)).andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(cookie -> cookie.startsWith("SESSION=") && cookie.contains("Max-Age=2592000"));
        assertThat(sessions.findById(laptop.sessionId()).getMaxInactiveInterval()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void turningItOffNeedsThePasswordAndSignsInWithThePasswordAgain() throws Exception {
        turnOn();
        owner.send(post("/api/account/two-factor/disable").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong-password-value\"}")).andExpect(status().isBadRequest());
        owner.send(post("/api/account/two-factor/disable").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\"}")).andExpect(status().isNoContent());

        assertThat(mail.last().subject()).isEqualTo("Wyłączono logowanie dwuetapowe w HourForge");
        new BrowserSession(mvc).login(EMAIL, PASSWORD).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from two_factor_backup_codes where user_id = ?", Integer.class, user.getId())).isZero();
    }

    @Test
    void newBackupCodesReplaceTheOldOnes() throws Exception {
        var old = turnOn();
        var renewed = JsonPath.<List<String>>read(owner.send(post("/api/account/two-factor/backup-codes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\"}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.codes");

        var laptop = new BrowserSession(mvc);
        laptop.login(EMAIL, PASSWORD).andExpect(status().isAccepted());
        answer(laptop, old.get(0)).andExpect(status().isBadRequest());
        answer(laptop, renewed.get(0)).andExpect(status().isNoContent());
    }

    @Test
    void deletingTheAccountRemovesTheSecretAndCodes() throws Exception {
        turnOn();
        owner.send(post("/api/account/delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from user_two_factor where user_id = ?", Integer.class, user.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from two_factor_backup_codes where user_id = ?", Integer.class, user.getId())).isZero();
    }

    @Test
    void theAccountEndpointsNeedASignedInPersonAndCsrf() throws Exception {
        mvc.perform(get("/api/account/two-factor")).andExpect(status().isUnauthorized());
        owner.perform(post("/api/account/two-factor/setup")).andExpect(status().isForbidden());
    }

    private List<String> turnOn() throws Exception {
        secret = JsonPath.read(owner.send(post("/api/account/two-factor/setup")).andReturn().getResponse().getContentAsString(), "$.secret");
        return JsonPath.read(enable(codeAt(0), PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.codes");
    }

    private ResultActions enable(String code, String password) throws Exception {
        return owner.send(post("/api/account/two-factor/enable").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"currentPassword\":\"" + password + "\"}"));
    }

    private ResultActions answer(BrowserSession browser, String code) throws Exception {
        return browser.send(post("/api/auth/two-factor").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    /** The app code {@code offset} steps from now; turning it on uses the current step, so sign-ins use the next one. */
    private String codeAt(int offset) {
        return Totp.code(secret, Totp.step(Instant.now()) + offset);
    }
}
