package pl.macieserafin.hourforge.mail;

/** Sends plain-text e-mails. Implementations must never throw for delivery problems. */
public interface EmailSender {
    void send(String to, String subject, String body);
}
