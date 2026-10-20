package pl.macieserafin.hourforge.auth.integration;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.auth.*;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;
import pl.macieserafin.hourforge.user.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationTransactionIT extends IntegrationTestSupport {
    private static final String PASSWORD = "Valid-password-2026!";
    private final String email = UUID.randomUUID() + "@registration.test";
    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean UserCredentialRepository credentials;
    @MockitoSpyBean PasswordEncoder encoder;

    @AfterEach void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("delete from email_tokens where user_id in (select id from users where lower(email) = ?)", email);
            jdbc.update("delete from user_credentials where user_id in (select id from users where lower(email) = ?)", email);
            jdbc.update("delete from users where lower(email) = ?", email);
        });
    }

    @Test void credentialFailureRollsBackTheAlreadyInsertedUser() {
        doThrow(new DataIntegrityViolationException("Simulated credential failure")).when(credentials).saveAndFlush(any(UserCredential.class));
        assertThatThrownBy(() -> registration.register(new RegistrationRequest("User", email, PASSWORD)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from user_credentials c join users u on u.id = c.user_id where lower(u.email) = ?", Long.class, email)).isZero();
    }

    @Test void concurrentCaseVariantsCreateOneCompleteAccountAndReturnOneConflict() throws Exception {
        var ready = new CountDownLatch(2);
        doAnswer(call -> {
            ready.countDown();
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Registration barrier timeout");
            return call.callRealMethod();
        }).when(encoder).encode(PASSWORD);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(email, email.toUpperCase(java.util.Locale.ROOT)).stream().map(address -> executor.submit(() -> {
                try { registration.register(new RegistrationRequest("User", address, PASSWORD)); return 201; }
                catch (ResponseStatusException exception) { return exception.getStatusCode().value(); }
            })).toList();
            assertThat(List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(encoder.matches(PASSWORD, credentials.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from users where lower(email) = ?", Long.class, email)).isEqualTo(1L);
    }
}
