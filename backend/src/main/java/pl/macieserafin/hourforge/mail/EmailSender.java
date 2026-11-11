package pl.macieserafin.hourforge.mail;

/** Sends e-mails. Implementations must never throw for delivery problems. */
public interface EmailSender {
    void send(Email email);
}
