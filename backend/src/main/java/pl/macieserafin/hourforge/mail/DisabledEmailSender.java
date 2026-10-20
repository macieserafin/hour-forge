package pl.macieserafin.hourforge.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when no mail server is configured, which keeps the application usable without e-mail. */
public class DisabledEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(DisabledEmailSender.class);

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public void send(String to, String subject, String body) {
        log.info("E-mail is not configured; message not sent: subject={}", subject);
    }
}
