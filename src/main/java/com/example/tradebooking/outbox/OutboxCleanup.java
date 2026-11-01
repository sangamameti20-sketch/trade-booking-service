package com.example.tradebooking.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/** Deletes published outbox rows once they are older than the retention period. */
@Component
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxCleanup {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanup.class);

    private final OutboxRepository repository;
    private final Clock clock;
    private final Duration retention;

    public OutboxCleanup(OutboxRepository repository, Clock clock,
                         @Value("${app.outbox.retention:7d}") Duration retention) {
        this.repository = repository;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron:0 15 * * * *}")
    @Transactional
    public void purge() {
        int deleted = repository.deletePublishedBefore(clock.instant().minus(retention));
        if (deleted > 0) {
            log.atInfo().addKeyValue("count", deleted).log("Purged published outbox events");
        }
    }
}
