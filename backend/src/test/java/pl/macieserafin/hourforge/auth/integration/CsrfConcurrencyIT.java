package pl.macieserafin.hourforge.auth.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.BrowserSession;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;
import pl.macieserafin.hourforge.user.User;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The first uses of the CSRF token in a fresh session, at once: the frontend asks for the token before every change, so
 * two changes sent together right after signing in ask for it twice. Real commits in the database session store, so no
 * test transaction.
 */
@AutoConfigureMockMvc
class CsrfConcurrencyIT extends IntegrationTestSupport {
    private static final String EMAIL = "parallel-csrf@example.com";
    private static final String PASSWORD = "Test-password-2026";
    private static final int PARALLEL = 8;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;

    private User user;

    @BeforeEach
    void account() {
        user = fixtures.user(EMAIL, "Parallel");
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
    }

    @AfterEach
    void removeAccount() {
        jdbc.update("delete from spring_session where principal_name = ?", user.getId().toString());
        jdbc.update("delete from login_events where user_id = ?", user.getId());
        jdbc.update("delete from user_credentials where user_id = ?", user.getId());
        jdbc.update("delete from users where id = ?", user.getId());
    }

    @Test
    void parallelFirstRequestsForTheTokenAllGetTheSameWorkingToken() throws Exception {
        for (int round = 0; round < 3; round++) {
            var browser = signedInSession();
            var errors = new CopyOnWriteArrayList<String>();
            var tokens = new CopyOnWriteArrayList<String>();

            var statuses = inParallel(errors, () -> {
                var response = mvc.perform(get("/api/auth/csrf").cookie(browser.cookie())).andReturn().getResponse();
                if (response.getStatus() == 200) tokens.add(JsonPath.read(response.getContentAsString(), "$.token"));
                return response.getStatus();
            });

            assertThat(statuses).as("round %d, errors %s", round, errors).hasSize(PARALLEL).containsOnly(200);
            // Each answer is masked differently (BREACH protection), but every one is accepted.
            for (var token : tokens) {
                browser.perform(patch("/api/account/profile").header("X-CSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Parallel\"}")).andExpect(status().isOk());
            }
        }
    }

    @Test
    void twoChangesSentTogetherRightAfterSigningInBothSucceed() throws Exception {
        for (int round = 0; round < 3; round++) {
            var browser = signedInSession();
            var errors = new CopyOnWriteArrayList<String>();

            // What the frontend does for each change: ask for the token, then send the change with it.
            var statuses = inParallel(errors, () -> {
                var csrf = mvc.perform(get("/api/auth/csrf").cookie(browser.cookie())).andReturn().getResponse();
                if (csrf.getStatus() != 200) return csrf.getStatus();
                String token = JsonPath.read(csrf.getContentAsString(), "$.token");
                return mvc.perform(patch("/api/account/profile").cookie(browser.cookie()).header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Parallel\"}")).andReturn().getResponse().getStatus();
            });

            assertThat(statuses).as("round %d, errors %s", round, errors).hasSize(PARALLEL).containsOnly(200);
        }
    }

    @Test
    void signingInStillReplacesTheToken() throws Exception {
        var browser = new BrowserSession(mvc);
        var before = browser.csrfToken();
        browser.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).header("X-CSRF-TOKEN", before)
                .param("email", EMAIL).param("password", PASSWORD)).andExpect(status().isNoContent());

        browser.perform(patch("/api/account/profile").header("X-CSRF-TOKEN", before).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Old token\"}")).andExpect(status().isForbidden());
        browser.send(patch("/api/account/profile").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"New token\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void aChangeWithoutTheTokenIsStillRefused() throws Exception {
        var browser = signedInSession();
        browser.perform(patch("/api/account/profile").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Nope\"}"))
                .andExpect(status().isForbidden());
        browser.perform(patch("/api/account/profile").header("X-CSRF-TOKEN", "forged").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Nope\"}")).andExpect(status().isForbidden());
    }

    /**
     * A fresh session after signing in, with one ordinary request first. Before the activity day moved out of the session, that request stored the day of
     * activity in the session, which isolated the CSRF token as the only attribute the parallel requests could still
     * add. The activity day is no longer a session attribute; the request stays and does no harm.
     */
    private BrowserSession signedInSession() throws Exception {
        var browser = new BrowserSession(mvc);
        browser.login(EMAIL, PASSWORD).andExpect(status().isNoContent());
        browser.perform(get("/api/me")).andExpect(status().isOk());
        return browser;
    }

    /** Runs {@code request} from {@value #PARALLEL} threads at once; a thrown error counts as 500 and is described in {@code errors}. */
    private List<Integer> inParallel(List<String> errors, Callable<Integer> request) throws Exception {
        var pool = Executors.newFixedThreadPool(PARALLEL);
        try {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < PARALLEL; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return request.call();
                    } catch (Exception exception) {
                        Throwable cause = exception;
                        while (cause.getCause() != null) cause = cause.getCause();
                        // The whole message: PostgreSQL names the duplicated key, i.e. the session attribute.
                        errors.add(exception.getClass().getSimpleName() + ": " + cause.getMessage().replace('\n', ' '));
                        return 500;
                    }
                }));
            }
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var future : futures) statuses.add(future.get(30, TimeUnit.SECONDS));
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
