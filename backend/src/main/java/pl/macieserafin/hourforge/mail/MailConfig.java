package pl.macieserafin.hourforge.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * E-mail is required: addresses are confirmed by link and passwords are reset by link. Set {@code SPRING_MAIL_HOST}
 * (with {@code SPRING_MAIL_PORT}, username and password as needed); {@code MAIL_FROM} sets the sender address and
 * {@code FRONTEND_URL} the base of the links in the messages. The application does not start without a mail server.
 */
@Configuration(proxyBeanMethods = false)
public class MailConfig {
    @Bean
    EmailSender smtpEmailSender(ObjectProvider<JavaMailSender> mailSender, @Value("${app.mail.from}") String from,
                                @Value("${spring.mail.host:}") String host) {
        var sender = mailSender.getIfAvailable();
        if (sender == null || host.isBlank()) {
            throw new IllegalStateException("A mail server is required. Set SPRING_MAIL_HOST (and SPRING_MAIL_PORT); "
                    + "for local development start Mailpit with Docker Compose and use localhost:1025.");
        }
        return new SmtpEmailSender(sender, from);
    }
}
