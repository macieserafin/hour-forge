package pl.macieserafin.hourforge.mail.unit;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import pl.macieserafin.hourforge.mail.Email;
import pl.macieserafin.hourforge.mail.SmtpEmailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** The message on the wire: what spam filters and mail clients look at before anybody reads it. */
class SmtpEmailSenderTest {
    private final SmtpEmailSender sender = new SmtpEmailSender(new JavaMailSenderImpl(), "HourForge <no-reply@mail.hourforge.example>");

    private static Email transactional() {
        return new Email("ala@example.test", "Reset hasła w HourForge", "Cześć Ala,\n\nlink:\nhttps://hourforge.example/r/abc\n",
                "<!DOCTYPE html><p>Cześć Ala, <a href=\"https://hourforge.example/r/abc\">link</a></p>", Email.Category.TRANSACTIONAL, null, null);
    }

    @Test
    void sendsTextAndHtmlAsAlternativesFromTheConfiguredSender() throws Exception {
        var message = sender.build(transactional());

        assertThat(message.getFrom()[0].toString()).isEqualTo("HourForge <no-reply@mail.hourforge.example>");
        assertThat(message.getSubject()).isEqualTo("Reset hasła w HourForge");
        assertThat(message.getContentType()).startsWith("multipart/alternative");
        var parts = (Multipart) message.getContent();
        assertThat(parts.getCount()).isEqualTo(2);
        assertThat(parts.getBodyPart(0).getContentType()).startsWith("text/plain").containsIgnoringCase("charset=UTF-8");
        assertThat(parts.getBodyPart(1).getContentType()).startsWith("text/html").containsIgnoringCase("charset=UTF-8");
        assertThat((String) parts.getBodyPart(0).getContent()).contains("https://hourforge.example/r/abc");
        assertThat((String) parts.getBodyPart(1).getContent()).contains("<a href=\"https://hourforge.example/r/abc\">");
    }

    @Test
    void marksItselfAsAutomaticWithAMessageIdOfTheSendersDomain() throws Exception {
        var message = sender.build(transactional());

        assertThat(message.getMessageID()).matches("<[0-9a-f-]{36}@mail\\.hourforge\\.example>");
        assertThat(message.getHeader("Auto-Submitted")).containsExactly("auto-generated");
        assertThat(message.getHeader("X-Auto-Response-Suppress")).containsExactly("All");
        assertThat(message.getHeader("Content-Language")).containsExactly("pl");
        assertThat(message.getSentDate()).isNotNull();
        // Transactional mail cannot be unsubscribed from.
        assertThat(message.getHeader("List-Unsubscribe")).isNull();
        assertThat(message.getHeader("List-Unsubscribe-Post")).isNull();
    }

    @Test
    void theMessageIdSurvivesTheSaveThatJavaMailSenderImplDoesBeforeSending() throws Exception {
        var message = sender.build(transactional());
        var id = message.getMessageID();

        // What JavaMailSenderImpl.doSend does with a message that already has an ID.
        message.saveChanges();
        message.setHeader("Message-ID", id);

        assertThat(wire(message)).contains("Message-ID: " + id);
    }

    @Test
    void notificationsOfferOneClickUnsubscribe() throws Exception {
        var email = new Email("ala@example.test", "S", "t", "<p>h</p>", Email.Category.NOTIFICATION,
                "https://hourforge.example/api/notifications/unsubscribe/one-click?token=abc&kind=WEEKLY_REPORT", null);

        var message = sender.build(email);

        assertThat(message.getHeader("List-Unsubscribe"))
                .containsExactly("<https://hourforge.example/api/notifications/unsubscribe/one-click?token=abc&kind=WEEKLY_REPORT>");
        assertThat(message.getHeader("List-Unsubscribe-Post")).containsExactly("List-Unsubscribe=One-Click");
    }

    @Test
    void anAttachmentGoesNextToTheAlternatives() throws Exception {
        var pdf = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
        var message = sender.build(transactional().withAttachment(new Email.Attachment("raport.pdf", pdf, "application/pdf")));

        assertThat(message.getContentType()).startsWith("multipart/mixed");
        var mixed = (Multipart) message.getContent();
        assertThat(mixed.getCount()).isEqualTo(2);
        assertThat(mixed.getBodyPart(0).getContentType()).startsWith("multipart/alternative");
        var file = mixed.getBodyPart(1);
        assertThat(file.getDisposition()).isEqualTo(Part.ATTACHMENT);
        assertThat(file.getFileName()).isEqualTo("raport.pdf");
        assertThat(file.getContentType()).startsWith("application/pdf");
        assertThat(file.getInputStream().readAllBytes()).isEqualTo(pdf);
    }

    private static String wire(MimeMessage message) throws Exception {
        var out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
