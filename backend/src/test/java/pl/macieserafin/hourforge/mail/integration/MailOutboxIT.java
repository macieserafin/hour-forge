package pl.macieserafin.hourforge.mail.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import pl.macieserafin.hourforge.mail.Email;
import pl.macieserafin.hourforge.mail.EmailSender;
import pl.macieserafin.hourforge.mail.MailTransport;
import pl.macieserafin.hourforge.mail.OutboxDelivery;
import pl.macieserafin.hourforge.mail.OutboxEmailSender;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamManagementService;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real commits on PostgreSQL: a message is stored in the outbox with the change that caused it, so it exists only when
 * the change is committed, survives until it is sent, and is tried again when the mail server fails.
 */
@TestPropertySource(properties = "test.mail.outbox=true")
class MailOutboxIT extends IntegrationTestSupport {
    @Autowired TeamManagementService management;
    @Autowired EmailSender sender;
    @Autowired @Qualifier("outboxEmailSender") EmailSender productionSender;
    @Autowired OutboxDelivery outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectProvider<MeterRegistry> meters;

    private final List<UUID> userIds = new ArrayList<>();
    private final String recipient = UUID.randomUUID() + "@outbox.example.test";
    private User owner;
    private User member;
    private Team team;

    @BeforeEach
    void fixturesOfTheTeam() {
        outbox.deliverDue();
        mail.clear();
        owner = account();
        member = account();
        team = fixtures.team("Mail outbox", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
    }

    @AfterEach
    void removeOwnedFixtures() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("drop trigger if exists reject_commit on teams");
            jdbc.execute("drop function if exists reject_commit()");
            jdbc.update("delete from outgoing_emails where recipient = ?", recipient);
            jdbc.update("delete from team_members where team_id = ?", team.getId());
            jdbc.update("delete from teams where id = ?", team.getId());
            for (UUID id : userIds) {
                jdbc.update("delete from user_preferences where user_id = ?", id);
                jdbc.update("delete from users where id = ?", id);
            }
        });
    }

    @Test
    void theApplicationSendsThroughTheOutbox() {
        assertThat(productionSender).isInstanceOf(OutboxEmailSender.class).isSameAs(sender);
    }

    @Test
    void archivingTellsTheMemberOnceTheChangeIsCommittedAndTheOutboxIsSent() {
        management.archive(owner.getId(), team.getId());

        assertThat(mail.sent()).isEmpty();
        assertThat(waiting()).isEqualTo(1);
        assertThat(outbox.deliverDue()).isEqualTo(1);
        assertThat(mail.sent()).extracting(m -> m.to()).containsExactly(member.getEmail());
        // A message that left is removed: its body may hold a one-time link.
        assertThat(waiting()).isZero();
    }

    @Test
    void aCommitThatFailsLeavesNothingToSend() {
        jdbc.execute("create function reject_commit() returns trigger language plpgsql as $$ begin raise exception 'commit rejected'; end $$");
        jdbc.execute("create constraint trigger reject_commit after update on teams initially deferred for each row execute function reject_commit()");

        assertThatThrownBy(() -> management.archive(owner.getId(), team.getId())).isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("select archived_at is null from teams where id = ?", Boolean.class, team.getId())).isTrue();
        assertThat(waiting()).isZero();
        outbox.deliverDue();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void mailOutsideATransactionIsStoredAtOnceAndARolledBackOneIsNot() {
        sender.send(plain(null));
        assertThat(rowsFor(recipient)).isEqualTo(1);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            sender.send(plain(null));
            status.setRollbackOnly();
        });
        assertThat(rowsFor(recipient)).isEqualTo(1);
    }

    @Test
    void anAttachmentLeavesUnchanged() {
        var pdf = new byte[] {'%', 'P', 'D', 'F', 0, 1, 2, (byte) 0xff};
        sender.send(plain(new Email.Attachment("report.pdf", pdf, "application/pdf")));

        outbox.deliverDue();

        var sent = mail.sent().stream().filter(m -> m.to().equals(recipient)).findFirst().orElseThrow();
        assertThat(sent.email().attachment().content()).isEqualTo(pdf);
        assertThat(sent.email().attachment().filename()).isEqualTo("report.pdf");
    }

    @Test
    void aFailingServerIsTriedAgainLaterAndFinallyGivenUp() {
        sender.send(plain(null));
        var start = Instant.now().plusSeconds(1);
        var attempts = new AtomicInteger();
        MailTransport down = email -> {
            if (email.to().equals(recipient)) attempts.incrementAndGet();
            throw new jakarta.mail.MessagingException("Connection refused");
        };

        var now = start;
        for (int attempt = 1; attempt <= 8; attempt++) {
            delivery(down, now).deliverDue();
            assertThat(attempts).hasValue(attempt);
            // Not due again until the waiting time has passed.
            delivery(down, now.plusSeconds(1)).deliverDue();
            assertThat(attempts).hasValue(attempt);
            now = now.plus(waitAfter(attempt)).plusSeconds(1);
        }
        assertThat(status(recipient)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select last_error from outgoing_emails where recipient = ?", String.class, recipient))
                .isEqualTo("MessagingException");
        delivery(down, now.plus(Duration.ofDays(1))).deliverDue();
        assertThat(attempts).hasValue(8);
        assertThat(failedGauge()).isPositive();

        assertThat(outbox.purgeFailed(now.plus(Duration.ofDays(6)))).isZero();
        assertThat(rowsFor(recipient)).isEqualTo(1);
        outbox.purgeFailed(now.plus(Duration.ofDays(8)));
        assertThat(rowsFor(recipient)).isZero();
    }

    @Test
    void aServerThatRecoversGetsTheMessage() {
        sender.send(plain(null));
        var now = Instant.now().plusSeconds(1);
        delivery(email -> { throw new jakarta.mail.MessagingException("Temporary failure"); }, now).deliverDue();
        assertThat(rowsFor(recipient)).isEqualTo(1);

        outbox.deliverDue();
        assertThat(rowsFor(recipient)).isEqualTo(1);
        delivery(mail::send, now.plus(Duration.ofMinutes(2))).deliverDue();
        assertThat(rowsFor(recipient)).isZero();
        assertThat(mail.sent()).extracting(m -> m.to()).contains(recipient);
    }

    /** 1, 2, 4 ... minutes after each failure, at most 6 hours. */
    private static Duration waitAfter(int failures) {
        var wait = Duration.ofMinutes(1L << (failures - 1));
        return wait.compareTo(Duration.ofHours(6)) > 0 ? Duration.ofHours(6) : wait;
    }

    private OutboxDelivery delivery(MailTransport transport, Instant now) {
        return new OutboxDelivery(jdbc, transport, transactionManager, Clock.fixed(now, ZoneOffset.UTC), new NoMeters());
    }

    private double failedGauge() {
        return meters.getObject().get("hourforge.mail.outbox").tag("status", "failed").gauge().value();
    }

    private int waiting() {
        return jdbc.queryForObject("select count(*) from outgoing_emails where recipient = ?", Integer.class, member.getEmail());
    }

    private int rowsFor(String to) {
        return jdbc.queryForObject("select count(*) from outgoing_emails where recipient = ?", Integer.class, to);
    }

    private String status(String to) {
        return jdbc.queryForObject("select status from outgoing_emails where recipient = ?", String.class, to);
    }

    private User account() {
        var user = fixtures.user(UUID.randomUUID() + "@mail-outbox.local", "Mail Test");
        userIds.add(user.getId());
        return user;
    }

    private Email plain(Email.Attachment attachment) {
        return new Email(recipient, "Subject", "Body", "<p>Body</p>", Email.Category.TRANSACTIONAL, null, attachment);
    }

    /** The meters are the application's; the extra deliveries of this test register none. */
    private static final class NoMeters implements ObjectProvider<MeterRegistry> {
        @Override
        public MeterRegistry getObject(Object... args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MeterRegistry getIfAvailable() {
            return null;
        }

        @Override
        public MeterRegistry getIfUnique() {
            return null;
        }

        @Override
        public MeterRegistry getObject() {
            throw new UnsupportedOperationException();
        }
    }
}
