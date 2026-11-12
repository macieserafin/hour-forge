package pl.macieserafin.hourforge.mail;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Stores a message in the outbox ({@code outgoing_emails}) as part of the current transaction, so it exists exactly
 * when the change that caused it is committed, and survives a restart. {@link OutboxDelivery} sends it shortly after.
 * Outside a transaction the message is stored at once.
 */
public class OutboxEmailSender implements EmailSender {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public OutboxEmailSender(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void send(Email email) {
        var now = Timestamp.from(clock.instant());
        var file = email.attachment();
        jdbc.update("""
                insert into outgoing_emails (id, recipient, subject, text_body, html_body, category, one_click_unsubscribe,
                    attachment_name, attachment_type, attachment_content, next_attempt_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), email.to(), email.subject(), email.text(), email.html(), email.category().name(),
                email.oneClickUnsubscribe(), file == null ? null : file.filename(), file == null ? null : file.contentType(),
                file == null ? null : file.content(), now, now);
    }
}
