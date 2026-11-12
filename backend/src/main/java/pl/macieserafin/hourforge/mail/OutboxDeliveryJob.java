package pl.macieserafin.hourforge.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Sends the outbox every few seconds. Unlike the other background jobs (app.jobs.enabled) it always runs, because
 * confirmation and password links must arrive; only tests switch it off ({@code app.mail.delivery.enabled=false}) and
 * deliver by hand.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.mail.delivery.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDeliveryJob {
    private static final Logger log = LoggerFactory.getLogger(OutboxDeliveryJob.class);

    private final OutboxDelivery outbox;

    public OutboxDeliveryJob(OutboxDelivery outbox) {
        this.outbox = outbox;
    }

    @Scheduled(initialDelayString = "PT5S", fixedDelayString = "PT3S")
    public void sendMail() {
        try {
            outbox.deliverDue();
        } catch (RuntimeException exception) {
            log.error("Mail delivery job failed", exception);
        }
    }
}
