package pl.macieserafin.hourforge.support;

import pl.macieserafin.hourforge.mail.EmailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Keeps sent messages in memory so tests can read the one-time links. */
public class RecordingEmailSender implements EmailSender {
    public record Message(String to, String subject, String body) {
    }

    private final List<Message> sent = new ArrayList<>();

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public synchronized void send(String to, String subject, String body) {
        sent.add(new Message(to, subject, body));
    }

    public synchronized List<Message> sent() {
        return List.copyOf(sent);
    }

    public synchronized void clear() {
        sent.clear();
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
