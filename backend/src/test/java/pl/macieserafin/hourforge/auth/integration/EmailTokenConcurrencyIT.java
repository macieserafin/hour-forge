package pl.macieserafin.hourforge.auth.integration;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.account.EmailChangeService;
import pl.macieserafin.hourforge.auth.EmailTokenPurpose;
import pl.macieserafin.hourforge.auth.EmailTokenService;
import pl.macieserafin.hourforge.auth.PasswordResetService;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

class EmailTokenConcurrencyIT extends IntegrationTestSupport {
    @Autowired EmailTokenService tokens;
    @Autowired PasswordResetService passwords;
    @Autowired EmailChangeService addresses;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean UserCredentialRepository credentials;

    private UUID userId;

    @AfterEach
    void cleanup() {
        if (userId == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("delete from email_tokens where user_id = ?", userId);
            jdbc.update("delete from user_credentials where user_id = ?", userId);
            jdbc.update("delete from users where id = ?", userId);
        });
    }

    @ParameterizedTest
    @EnumSource(value = EmailTokenPurpose.class, names = {"RESET_PASSWORD", "CHANGE_EMAIL"})
    void aLinkCannotRedeemWhileAnEarlierCredentialChangeCommits(EmailTokenPurpose purpose) throws Exception {
        var transaction = new TransactionTemplate(transactionManager);
        var email = UUID.randomUUID() + "@token-race.test";
        var hash = encoder.encode("Current-password-2026");
        var raw = transaction.execute(status -> {
            var user = fixtures.user(email, "Token race");
            userId = user.getId();
            credentials.saveAndFlush(new UserCredential(user, hash));
            return tokens.issue(user, purpose, purpose == EmailTokenPurpose.CHANGE_EMAIL ? "new-" + email : null);
        });
        var redemptionReachedCredentials = new CountDownLatch(1);
        // Spring Data's spy delegates interface methods to the real repository proxy.
        var repositoryAnswer = mockingDetails(credentials).getMockCreationSettings().getDefaultAnswer();

        try (var executor = Executors.newSingleThreadExecutor()) {
            var redemption = transaction.execute(status -> {
                var credential = credentials.findLockedByUserId(userId).orElseThrow();
                doAnswer(call -> {
                    redemptionReachedCredentials.countDown();
                    return repositoryAnswer.answer(call);
                }).when(credentials).findLockedByUserId(userId);
                var future = executor.submit(() -> {
                    try {
                        if (purpose == EmailTokenPurpose.RESET_PASSWORD) passwords.confirm(raw, "Brand-new-password-2026");
                        else addresses.confirm(raw);
                        return 204;
                    } catch (ResponseStatusException exception) {
                        return exception.getStatusCode().value();
                    }
                });
                try {
                    assertThat(redemptionReachedCredentials.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                // Commit the same version change used by password changes and "sign out everywhere" while
                // redemption is waiting for this row. It must inspect the committed version after the wait.
                credential.invalidateSessions();
                return future;
            });
            assertThat(redemption.get(15, TimeUnit.SECONDS)).isEqualTo(400);
        }

        assertThat(jdbc.queryForObject("select password_hash from user_credentials where user_id = ?", String.class, userId))
                .isEqualTo(hash);
        assertThat(jdbc.queryForObject("select email from users where id = ?", String.class, userId)).isEqualTo(email);
        assertThat(jdbc.queryForObject("select used_at is null from email_tokens where user_id = ?", Boolean.class, userId)).isTrue();
    }
}
