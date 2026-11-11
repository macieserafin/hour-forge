package pl.macieserafin.hourforge.mail.integration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.macieserafin.hourforge.mail.Email;
import pl.macieserafin.hourforge.mail.EmailSender;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamManagementService;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real commits on PostgreSQL: a message about a change leaves only once the change is stored. */
@TestPropertySource(properties = "test.mail.after-commit=true")
class MailAfterCommitIT extends IntegrationTestSupport {
    @Autowired TeamManagementService management;
    @Autowired EmailSender sender;
    @Autowired @Qualifier("smtpEmailSender") EmailSender productionSender;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private final List<UUID> userIds = new ArrayList<>();
    private User owner;
    private User member;
    private Team team;

    @BeforeEach
    void fixturesOfTheTeam() {
        owner = account();
        member = account();
        team = fixtures.team("Mail after commit", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
    }

    @AfterEach
    void removeOwnedFixtures() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.execute("drop trigger if exists reject_commit on teams");
            jdbc.execute("drop function if exists reject_commit()");
            jdbc.update("delete from team_members where team_id = ?", team.getId());
            jdbc.update("delete from teams where id = ?", team.getId());
            for (UUID id : userIds) {
                jdbc.update("delete from user_preferences where user_id = ?", id);
                jdbc.update("delete from users where id = ?", id);
            }
        });
    }

    @Test
    void archivingTellsTheMemberOnlyAfterTheChangeIsCommitted() {
        management.archive(owner.getId(), team.getId());

        assertThat(jdbc.queryForObject("select archived_at is not null from teams where id = ?", Boolean.class, team.getId())).isTrue();
        assertThat(mail.sent()).extracting(m -> m.to()).containsExactly(member.getEmail());
    }

    @Test
    void aCommitThatFailsSendsNothingAboutTheChange() {
        jdbc.execute("create function reject_commit() returns trigger language plpgsql as $$ begin raise exception 'commit rejected'; end $$");
        jdbc.execute("create constraint trigger reject_commit after update on teams initially deferred for each row execute function reject_commit()");

        assertThatThrownBy(() -> management.archive(owner.getId(), team.getId())).isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("select archived_at is null from teams where id = ?", Boolean.class, team.getId())).isTrue();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void mailSentOutsideAnyTransactionLeavesAtOnce() {
        sender.send(plain());

        assertThat(mail.sent()).hasSize(1);
    }

    @Test
    void mailSentInATransactionThatRollsBackIsDropped() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            sender.send(plain());
            status.setRollbackOnly();
        });

        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void theProductionSenderDefersDeliveryUntilCommit() {
        assertThat(productionSender).isInstanceOf(pl.macieserafin.hourforge.mail.AfterCommitEmailSender.class);
    }

    private User account() {
        var user = fixtures.user(UUID.randomUUID() + "@mail-after-commit.local", "Mail Test");
        userIds.add(user.getId());
        return user;
    }

    private static Email plain() {
        return new Email("someone@example.test", "Subject", "Body", "<p>Body</p>", Email.Category.TRANSACTIONAL, null, null);
    }
}
