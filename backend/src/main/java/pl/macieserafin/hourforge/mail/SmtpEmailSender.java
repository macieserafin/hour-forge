package pl.macieserafin.hourforge.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Delivers mail in the background so a slow or failing server never delays or breaks the request. */
public class SmtpEmailSender implements EmailSender, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "email-sender");
        thread.setDaemon(true);
        return thread;
    });

    public SmtpEmailSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        executor.execute(() -> {
            try {
                var message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
            } catch (RuntimeException exception) {
                // The body holds a one-time link, so only the subject and failure type are logged.
                log.warn("E-mail delivery failed: subject={}, error={}", subject, exception.getClass().getSimpleName());
            }
        });
    }

    @Override
    public void sendWithAttachment(String to, String subject, String body, String filename, byte[] content, String contentType) {
        executor.execute(() -> {
            try {
                var message = mailSender.createMimeMessage();
                var helper = new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(body);
                helper.addAttachment(filename, new org.springframework.core.io.ByteArrayResource(content), contentType);
                mailSender.send(message);
            } catch (jakarta.mail.MessagingException | RuntimeException exception) {
                log.warn("E-mail delivery failed: subject={}, error={}", subject, exception.getClass().getSimpleName());
            }
        });
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
