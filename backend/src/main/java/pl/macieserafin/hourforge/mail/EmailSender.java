package pl.macieserafin.hourforge.mail;

/** Sends plain-text e-mails. Implementations must never throw for delivery problems. */
public interface EmailSender {
    /** False when no mail server is configured; callers then skip flows that depend on e-mail. */
    boolean enabled();

    void send(String to, String subject, String body);
}
