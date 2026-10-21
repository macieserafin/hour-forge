package pl.macieserafin.hourforge.mail;

/** Sends plain-text e-mails. Implementations must never throw for delivery problems. */
public interface EmailSender {
    void send(String to, String subject, String body);

    /** Like {@link #send} with one file attached; a sender without attachment support says so in the text. */
    default void sendWithAttachment(String to, String subject, String body, String filename, byte[] content, String contentType) {
        send(to, subject, body + "\n(Załącznik " + filename + " jest niedostępny w tej konfiguracji poczty.)\n");
    }
}
