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
import org.springframework.mail.javamail.JavaMailSender;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Sends one message over SMTP and waits for the server's answer. Used by the outbox delivery job ({@link OutboxDelivery}),
 * never inside a request: a slow or failing server delays only the job, and a failed message is tried again.
 */
public class SmtpEmailSender implements MailTransport {
    private final JavaMailSender mailSender;
    private final InternetAddress from;
    private final String messageIdDomain;

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
    public void deliver(Email email) throws MessagingException, UnsupportedEncodingException {
        mailSender.send(build(email));
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
}
