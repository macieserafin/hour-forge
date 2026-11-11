package pl.macieserafin.hourforge.support;

import pl.macieserafin.hourforge.mail.Email;
import pl.macieserafin.hourforge.mail.EmailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Keeps sent messages in memory so tests can read the one-time links. */
public class RecordingEmailSender implements EmailSender {
    /** {@code body} is the plain-text version; {@code email} holds the HTML, category and headers' data too. */
    public record Message(String to, String subject, String body, Email email) {
        public String html() {
            return email.html();
        }
    }

    private final List<Message> sent = new ArrayList<>();
    private final List<String> attachments = new ArrayList<>();

    @Override
    public synchronized void send(Email email) {
        var file = email.attachment();
        if (file != null) {
            if (file.content().length == 0) throw new IllegalStateException("empty attachment");
            attachments.add(email.to() + ":" + file.filename() + ":" + file.contentType());
        }
        sent.add(new Message(email.to(), email.subject(), email.text(), email));
    }

    public synchronized List<String> attachments() {
        return List.copyOf(attachments);
    }

    public synchronized List<Message> sent() {
        return List.copyOf(sent);
    }

    public synchronized void clear() {
        sent.clear();
        attachments.clear();
    }

    public synchronized Message last() {
        return sent.get(sent.size() - 1);
    }

    /** The token of the link {@code /<path>/<token>} in the most recent message. */
    public String lastToken(String path) {
        var matcher = Pattern.compile("/" + Pattern.quote(path) + "/([A-Za-z0-9_-]+)").matcher(last().body());
        if (!matcher.find()) throw new IllegalStateException("No /" + path + "/ link in the last message");
        return matcher.group(1);
    }
}
