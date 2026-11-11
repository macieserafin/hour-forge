package pl.macieserafin.hourforge.google.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.support.BrowserSession;
import pl.macieserafin.hourforge.support.FakeGoogle.Account;
import pl.macieserafin.hourforge.support.GoogleSignInTestSupport;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Two first sign-ins with Google at the same moment. Real commits, so no test transaction: a table lock held by
 * the test makes both requests pass their checks and wait at the insert, then lets them race on the unique indexes.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GoogleSignInRaceIT extends GoogleSignInTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @AfterEach
    void removeTheAccounts() {
        var ids = jdbc.queryForList("select id from users where email like 'race-%@gmail.com'", java.util.UUID.class);
        for (var id : ids) {
            jdbc.update("delete from user_identities where user_id = ?", id);
            jdbc.update("delete from login_events where user_id = ?", id);
            jdbc.update("delete from email_tokens where user_id = ?", id);
            jdbc.update("delete from user_credentials where user_id = ?", id);
            jdbc.update("delete from users where id = ?", id);
        }
    }

    @Test
    void theSameGoogleAccountTwiceAtOnceMakesOneAccount() throws Exception {
        var results = race(Account.gmail("race-sub-1", "race-one@gmail.com"), Account.gmail("race-sub-1", "race-one@gmail.com"));

        assertThat(results).containsExactlyInAnyOrder(FRONTEND + "/dashboard", FRONTEND + "/login?google=GOOGLE_ACCOUNT_EXISTS");
        assertThat(count("select count(*) from users where email = 'race-one@gmail.com'")).isOne();
        assertThat(count("select count(*) from user_identities where subject = 'race-sub-1'")).isOne();
        // Signing in again with that Google account now simply works.
        GOOGLE.account = Account.gmail("race-sub-1", "race-one@gmail.com");
        signInWithGoogle(new BrowserSession(mvc)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .redirectedUrl(FRONTEND + "/dashboard"));
    }

    @Test
    void twoGoogleAccountsWithTheSameAddressAtOnceMakeOneAccount() throws Exception {
        var results = race(Account.gmail("race-sub-2", "race-two@gmail.com"), Account.gmail("race-sub-3", "race-two@gmail.com"));

        assertThat(results).containsExactlyInAnyOrder(FRONTEND + "/dashboard", FRONTEND + "/login?google=GOOGLE_ACCOUNT_EXISTS");
        assertThat(count("select count(*) from users where email = 'race-two@gmail.com'")).isOne();
        assertThat(count("select count(*) from user_identities where subject in ('race-sub-2', 'race-sub-3')")).isOne();
    }

    @Test
    void theSameGoogleAccountWithTwoAddressesAtOnceLinksOnlyOnce() throws Exception {
        // Google changed the address between the two consents: different addresses, the same sub.
        var results = race(Account.gmail("race-sub-4", "race-four@gmail.com"), Account.gmail("race-sub-4", "race-five@gmail.com"));

        assertThat(results).containsExactlyInAnyOrder(FRONTEND + "/dashboard", FRONTEND + "/login?google=GOOGLE_FAILED");
        assertThat(count("select count(*) from user_identities where subject = 'race-sub-4'")).isOne();
        // The losing request rolled back its account too.
        assertThat(count("select count(*) from users where email in ('race-four@gmail.com', 'race-five@gmail.com')")).isOne();
    }

    /** Both browsers get their callback ready, then return at once while the test holds the users table. */
    private List<String> race(Account first, Account second) throws Exception {
        var callbacks = new ArrayList<URI>();
        var browsers = List.of(new BrowserSession(mvc).device(null, "203.0.113.71"), new BrowserSession(mvc).device(null, "203.0.113.72"));
        for (int i = 0; i < 2; i++) {
            GOOGLE.account = i == 0 ? first : second;
            callbacks.add(GOOGLE.consent(start(browsers.get(i), "mode=login").andReturn().getResponse().getRedirectedUrl()));
        }
        var pool = Executors.newFixedThreadPool(2);
        try (Connection lock = dataSource.getConnection()) {
            lock.setAutoCommit(false);
            lock.createStatement().execute("LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE");
            var futures = new ArrayList<Future<String>>();
            for (int i = 0; i < 2; i++) {
                var browser = browsers.get(i);
                var callback = callbacks.get(i);
                futures.add(pool.submit((Callable<String>) () -> browser.perform(get(callback)).andReturn().getResponse().getRedirectedUrl()));
            }
            awaitWaitingInserts(2);
            lock.commit();
            var results = new ArrayList<String>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private void awaitWaitingInserts(int expected) throws InterruptedException {
        var deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            if (count("select count(*) from pg_locks l join pg_class c on c.oid = l.relation where c.relname = 'users' and not l.granted") >= expected) return;
            Thread.sleep(20);
        }
        throw new AssertionError("The two sign-ins did not both reach the insert");
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
