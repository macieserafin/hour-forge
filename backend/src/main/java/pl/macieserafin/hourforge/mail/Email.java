package pl.macieserafin.hourforge.mail;

import java.util.Objects;

/**
 * One e-mail ready to leave: the same content as plain text and as HTML, so every client shows it and filters see a
 * normal message. A notification carries the address that switches it off in one click (RFC 8058).
 */
public record Email(String to, String subject, String text, String html, Category category, String oneClickUnsubscribe,
                    Attachment attachment) {
    /** Transactional mail answers something the person did or must know; a notification is optional and can be stopped. */
    public enum Category { TRANSACTIONAL, NOTIFICATION }

    public record Attachment(String filename, byte[] content, String contentType) {
        public Attachment {
            Objects.requireNonNull(filename);
            Objects.requireNonNull(content);
            Objects.requireNonNull(contentType);
        }
    }

    public Email {
        Objects.requireNonNull(to);
        Objects.requireNonNull(subject);
        Objects.requireNonNull(text);
        Objects.requireNonNull(html);
        Objects.requireNonNull(category);
        if (category == Category.NOTIFICATION && oneClickUnsubscribe == null) {
            throw new IllegalArgumentException("A notification needs a one-click unsubscribe address");
        }
    }

    public Email withAttachment(Attachment file) {
        return new Email(to, subject, text, html, category, oneClickUnsubscribe, file);
    }
}
