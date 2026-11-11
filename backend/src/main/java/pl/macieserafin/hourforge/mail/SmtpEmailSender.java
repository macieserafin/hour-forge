package pl.macieserafin.hourforge.mail;

import jakarta.activation.DataHandler;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Delivers mail in the background so a slow or failing server never delays or breaks the request. */
public class SmtpEmailSender implements EmailSender, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final InternetAddress from;
    private final String messageIdDomain;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "email-sender");
        thread.setDaemon(true);
        return thread;
    });

    public SmtpEmailSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        try {
            this.from = new InternetAddress(from, true);
        } catch (AddressException exception) {
            throw new IllegalStateException("MAIL_FROM is not a valid address", exception);
        }
        var address = this.from.getAddress();
        this.messageIdDomain = address.substring(address.indexOf('@') + 1);
    }

    @Override
    public void send(Email email) {
        executor.execute(() -> {
            try {
                mailSender.send(build(email));
            } catch (MessagingException | UnsupportedEncodingException | RuntimeException exception) {
                // The body holds a one-time link, so only the subject and failure type are logged.
                log.warn("E-mail delivery failed: subject={}, error={}", email.subject(), exception.getClass().getSimpleName());
            }
        });
    }

    /**
     * The message as filters expect a well-behaved sender: text and HTML alternatives, a Message-ID of the sender's own
     * domain, marked as automatic (no out-of-office replies), and a one-click unsubscribe for notifications.
     */
    public MimeMessage build(Email email) throws MessagingException, UnsupportedEncodingException {
        var message = mailSender.createMimeMessage();
        message.setFrom(from);
        message.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(email.to(), true));
        message.setSubject(email.subject(), StandardCharsets.UTF_8.name());
        message.setSentDate(new Date());
        message.setHeader("Auto-Submitted", "auto-generated");
        message.setHeader("X-Auto-Response-Suppress", "All");
        message.setHeader("Content-Language", "pl");
        if (email.oneClickUnsubscribe() != null) {
            message.setHeader("List-Unsubscribe", "<" + email.oneClickUnsubscribe() + ">");
            message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        }

        var alternatives = new MimeMultipart("alternative");
        alternatives.addBodyPart(textPart(email.text(), "plain"));
        alternatives.addBodyPart(textPart(email.html(), "html"));
        if (email.attachment() == null) {
            message.setContent(alternatives);
        } else {
            var body = new MimeBodyPart();
            body.setContent(alternatives);
            var file = new MimeBodyPart();
            var attachment = email.attachment();
            file.setDataHandler(new DataHandler(new ByteArrayDataSource(attachment.content(), attachment.contentType())));
            file.setFileName(attachment.filename());
            file.setDisposition(Part.ATTACHMENT);
            var mixed = new MimeMultipart("mixed");
            mixed.addBodyPart(body);
            mixed.addBodyPart(file);
            message.setContent(mixed);
        }
        message.saveChanges();
        // After saving, which would replace it with one naming the sending machine; JavaMailSenderImpl keeps it as it is.
        message.setHeader("Message-ID", "<" + UUID.randomUUID() + "@" + messageIdDomain + ">");
        return message;
    }

    private static MimeBodyPart textPart(String content, String subtype) throws MessagingException {
        var part = new MimeBodyPart();
        part.setText(content, StandardCharsets.UTF_8.name(), subtype);
        part.setHeader("Content-Transfer-Encoding", "quoted-printable");
        return part;
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
