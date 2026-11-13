package pl.macieserafin.hourforge.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lets through only messages for the listed addresses ({@code MAIL_ALLOWED_RECIPIENTS}), for testing a real mail server
 * on a developer's computer: older messages in the local outbox, for made-up or other people's addresses, never reach
 * the real server. They fail like any undelivered message: tried again on the outbox schedule (by Mailpit, once the
 * developer switches back) and marked failed after the last attempt.
 */
public class AllowedRecipientsTransport implements MailTransport {
    private final MailTransport delegate;
    private final Set<String> allowed;

    public AllowedRecipientsTransport(MailTransport delegate, String allowedRecipients) {
        this.delegate = delegate;
        this.allowed = Arrays.stream(allowedRecipients.split(","))
                .map(String::strip).filter(address -> !address.isEmpty())
                .map(address -> address.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        if (allowed.isEmpty()) throw new IllegalArgumentException("No allowed recipients given");
    }

    @Override
    public void deliver(Email email) throws Exception {
        for (var recipient : InternetAddress.parse(email.to(), true)) {
            if (!allowed.contains(recipient.getAddress().toLowerCase(Locale.ROOT))) {
                throw new RecipientNotAllowedException();
            }
        }
        delegate.deliver(email);
    }

    /** The address is not on the list; the outbox logs only this type, never the address. */
    public static class RecipientNotAllowedException extends MessagingException {
        RecipientNotAllowedException() {
            super("Recipient is not in MAIL_ALLOWED_RECIPIENTS");
        }
    }
}
