package pl.macieserafin.hourforge.mail;

/** Hands one message to the mail server and waits for its answer; throws when the server did not take it. */
public interface MailTransport {
    void deliver(Email email) throws Exception;
}
