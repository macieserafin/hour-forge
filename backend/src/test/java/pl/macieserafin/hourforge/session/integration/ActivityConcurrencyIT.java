package pl.macieserafin.hourforge.session.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.BrowserSession;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;
import pl.macieserafin.hourforge.user.User;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The first requests of a new session, sent at once (a page loading several things after the sign-in). Each one goes
 * through the real filters and the database session store and commits, so there is no test transaction.
 */
@AutoConfigureMockMvc
class ActivityConcurrencyIT extends IntegrationTestSupport {
    private static final String EMAIL = "parallel-activity@example.com";
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
    void parallelFirstRequestsOfANewSessionAllSucceedAndTheDayIsRecorded() throws Exception {
        jdbc.update("update users set last_active_at = null where id = ?", user.getId());
        // Several new sessions, each racing on its first requests.
        for (int round = 0; round < 3; round++) {
            var browser = new BrowserSession(mvc);
            browser.login(EMAIL, PASSWORD).andExpect(status().isNoContent());

            var errors = new java.util.concurrent.CopyOnWriteArrayList<String>();
            var statuses = inParallel(browser, errors);

            assertThat(statuses).as("round %d, errors %s", round, errors).hasSize(PARALLEL).containsOnly(200);
            var recorded = jdbc.queryForObject("select last_active_at from users where id = ?", Timestamp.class, user.getId());
            assertThat(recorded).isNotNull();
            assertThat(recorded.toInstant()).isEqualTo(Instant.now().truncatedTo(ChronoUnit.DAYS));
            // The session still works afterwards.
            browser.perform(get("/api/me")).andExpect(status().isOk());
        }
    }

    /** {@code GET /api/me} from {@value #PARALLEL} threads at once, on the same session cookie; a thrown error counts as 500. */
    private ArrayList<Integer> inParallel(BrowserSession browser, java.util.List<String> errors) throws Exception {
        var pool = Executors.newFixedThreadPool(PARALLEL);
        try {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < PARALLEL; i++) {
                futures.add(pool.submit((Callable<Integer>) () -> {
                    start.await();
                    try {
                        return mvc.perform(get("/api/me").cookie(browser.cookie())).andReturn().getResponse().getStatus();
                    } catch (Exception exception) {
                        Throwable cause = exception;
                        while (cause.getCause() != null) cause = cause.getCause();
                        errors.add(exception.getClass().getSimpleName() + ": " + cause.getMessage().lines().findFirst().orElse(""));
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
