package pl.macieserafin.hourforge.mail;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the messages waiting in the outbox. Each message is claimed in its own transaction ({@code for update skip
 * locked}), handed to the mail server and removed once the server took it. A failure is tried again later, waiting
 * longer each time (1, 2, 4 ... minutes, at most 6 hours); after {@value #MAX_ATTEMPTS} attempts the message is marked
 * FAILED for the operator (metric {@code hourforge_mail_outbox{status="failed"}}). Delivery is at least once: a message
 * the server took just before a crash may leave twice.
 */
@Component
public class OutboxDelivery {
    static final int MAX_ATTEMPTS = 8;
    static final Duration MAX_DELAY = Duration.ofHours(6);
    static final Duration KEEP_FAILED = Duration.ofDays(7);
    private static final int BATCH = 50;
    private static final Logger log = LoggerFactory.getLogger(OutboxDelivery.class);

    private final JdbcTemplate jdbc;
    private final MailTransport transport;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public OutboxDelivery(JdbcTemplate jdbc, MailTransport transport, PlatformTransactionManager transactionManager, Clock clock,
                          ObjectProvider<MeterRegistry> registry) {
        this.jdbc = jdbc;
        this.transport = transport;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        registry.ifAvailable(meters -> {
            Gauge.builder("hourforge.mail.outbox", () -> count("PENDING")).tag("status", "pending")
                    .description("E-mails waiting to be sent").register(meters);
            Gauge.builder("hourforge.mail.outbox", () -> count("FAILED")).tag("status", "failed")
                    .description("E-mails that could not be sent after all attempts").register(meters);
        });
    }

    /** Sends what is due now; returns how many messages left. */
    public int deliverDue() {
        int sent = 0;
        for (int i = 0; i < BATCH; i++) {
            var result = transactions.execute(status -> deliverOne(clock.instant()));
            if (result == null || result.isEmpty()) break;
            if (result.get()) sent++;
        }
        return sent;
    }

    /** Removes messages that failed for good more than a week ago. */
    public int purgeFailed(Instant now) {
        return jdbc.update("delete from outgoing_emails where status = 'FAILED' and next_attempt_at < ?",
                Timestamp.from(now.minus(KEEP_FAILED)));
    }

    /** Empty when nothing is due; otherwise whether the claimed message was sent. */
    private Optional<Boolean> deliverOne(Instant now) {
        var due = jdbc.query("""
                select id, recipient, subject, text_body, html_body, category, one_click_unsubscribe, attachment_name,
                       attachment_type, attachment_content, attempts
                from outgoing_emails
                where status = 'PENDING' and next_attempt_at <= ?
                order by next_attempt_at
                limit 1
                for update skip locked
                """, (row, n) -> new Claimed(row.getObject("id", UUID.class), new Email(row.getString("recipient"),
                        row.getString("subject"), row.getString("text_body"), row.getString("html_body"),
                        Email.Category.valueOf(row.getString("category")), row.getString("one_click_unsubscribe"),
                        row.getString("attachment_name") == null ? null : new Email.Attachment(row.getString("attachment_name"),
                                row.getBytes("attachment_content"), row.getString("attachment_type"))),
                        row.getInt("attempts")), Timestamp.from(now));
        if (due.isEmpty()) return Optional.empty();
        var claimed = due.getFirst();
        try {
            transport.deliver(claimed.email());
            jdbc.update("delete from outgoing_emails where id = ?", claimed.id());
            return Optional.of(true);
        } catch (Exception exception) {
            fail(claimed, exception, now);
            return Optional.of(false);
        }
    }

    private void fail(Claimed claimed, Exception exception, Instant now) {
        int attempts = claimed.attempts() + 1;
        // The body holds one-time links and the address is personal data: only the subject and the failure type are kept.
        var error = exception.getClass().getSimpleName();
        if (attempts >= MAX_ATTEMPTS) {
            jdbc.update("update outgoing_emails set status = 'FAILED', attempts = ?, last_error = ?, next_attempt_at = ? where id = ?",
                    attempts, error, Timestamp.from(now), claimed.id());
            log.error("E-mail not delivered after {} attempts, given up: id={}, subject={}, error={}", attempts, claimed.id(),
                    claimed.email().subject(), error);
            return;
        }
        var delay = delayAfter(attempts);
        jdbc.update("update outgoing_emails set attempts = ?, last_error = ?, next_attempt_at = ? where id = ?",
                attempts, error, Timestamp.from(now.plus(delay)), claimed.id());
        log.warn("E-mail delivery failed, next attempt in {}: id={}, subject={}, error={}", delay, claimed.id(),
                claimed.email().subject(), error);
    }

    /** 1 minute after the first failure, then twice as long each time, at most {@link #MAX_DELAY}. */
    static Duration delayAfter(int attempts) {
        var delay = Duration.ofMinutes(1L << Math.min(attempts - 1, 20));
        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }

    private double count(String status) {
        Long count = jdbc.queryForObject("select count(*) from outgoing_emails where status = ?", Long.class, status);
        return count == null ? 0 : count;
    }

    private record Claimed(UUID id, Email email, int attempts) {
    }
}
