package pl.macieserafin.hourforge.support;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import pl.macieserafin.hourforge.mail.EmailSender;
import pl.macieserafin.hourforge.mail.MailTransport;

/**
 * Mail is recorded at once by default, which suits the tests that roll back their transaction. Tests of commits and
 * rollbacks set {@code test.mail.outbox=true} to get the delivery of production: messages are stored in the outbox with
 * the change and reach the recorder when the test runs {@code OutboxDelivery.deliverDue()}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfig {
    @Bean
    RecordingEmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "test.mail.outbox", havingValue = "true")
    MailTransport recordingTransport(RecordingEmailSender recording) {
        return recording::send;
    }

    /** The recorder is an EmailSender too; the application must still send through the outbox. */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "test.mail.outbox", havingValue = "true")
    EmailSender primaryOutbox(@Qualifier("outboxEmailSender") EmailSender outbox) {
        return outbox;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "test.mail.outbox", havingValue = "false", matchIfMissing = true)
    EmailSender immediateRecordingSender(RecordingEmailSender recording) {
        return recording;
    }

    @Bean
    @Primary
    TestBotCheck testBotCheck() {
        return new TestBotCheck();
    }
}
