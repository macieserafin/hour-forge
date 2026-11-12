package pl.macieserafin.hourforge.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityEventsTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(SecurityEvents.LOGGER);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void attach() {
        logged.start();
        logger.addAppender(logged);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(logged);
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void writesTheEventWithItsFieldsAtOnceOutsideATransaction() {
        var actor = UUID.randomUUID();
        var team = UUID.randomUUID();

        SecurityEvents.record("team.deleted", actor, "team", team, "skipped", null);

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key).containsExactly("event", "actor", "team");
            assertThat(event.getFormattedMessage()).isEqualTo("security event event=team.deleted actor=" + actor + " team=" + team);
        });
    }

    @Test
    void waitsForTheCommitAndForgetsARolledBackChange() {
        TransactionSynchronizationManager.initSynchronization();
        SecurityEvents.record("team.member-removed", UUID.randomUUID());
        assertThat(logged.list).isEmpty();
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(logged.list).isEmpty();
        TransactionSynchronizationManager.clearSynchronization();

        TransactionSynchronizationManager.initSynchronization();
        SecurityEvents.record("team.member-removed", UUID.randomUUID());
        TransactionSynchronizationUtils.triggerAfterCommit();
        assertThat(logged.list).hasSize(1);
    }

    @Test
    void detailsComeInPairs() {
        assertThatThrownBy(() -> SecurityEvents.record("x", null, "team")).isInstanceOf(IllegalArgumentException.class);
    }
}
