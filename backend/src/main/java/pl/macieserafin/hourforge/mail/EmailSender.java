package pl.macieserafin.hourforge.mail;

/** Queues e-mails for sending (see {@link OutboxEmailSender}); a problem with the mail server never reaches the caller. */
public interface EmailSender {
    void send(Email email);
}
