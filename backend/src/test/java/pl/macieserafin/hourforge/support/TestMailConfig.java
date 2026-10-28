package pl.macieserafin.hourforge.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pl.macieserafin.hourforge.mail.AfterCommitEmailSender;
import pl.macieserafin.hourforge.mail.EmailSender;

/**
 * Mail is recorded at once by default, which suits the tests that roll back their transaction. Tests of commits and
 * rollbacks set {@code test.mail.after-commit=true} to get the delivery rule of production: mail leaves only after commit.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfig {
    @Bean
    RecordingEmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "test.mail.after-commit", havingValue = "true")
    EmailSender afterCommitRecordingSender(RecordingEmailSender recording) {
        return new AfterCommitEmailSender(recording);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "test.mail.after-commit", havingValue = "false", matchIfMissing = true)
    EmailSender immediateRecordingSender(RecordingEmailSender recording) {
        return recording;
    }

    @Bean
    @Primary
    TestBotCheck testBotCheck() {
        return new TestBotCheck();
    }
}
