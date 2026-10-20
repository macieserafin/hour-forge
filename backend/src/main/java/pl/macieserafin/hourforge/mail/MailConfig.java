package pl.macieserafin.hourforge.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Mail is optional. Setting {@code SPRING_MAIL_HOST} (with port, username and password as needed) turns it on;
 * {@code MAIL_FROM} sets the sender address and {@code FRONTEND_URL} the base of the links in the messages.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class MailConfig {
    @Bean
    @ConditionalOnExpression("!'${spring.mail.host:}'.isEmpty()")
    EmailSender smtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        return new SmtpEmailSender(mailSender, from);
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender disabledEmailSender() {
        return new DisabledEmailSender();
    }
}
