package com.example.tradebooking.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox and publishes pending events to Kafka (at-least-once).
 *
 * <ul>
 *   <li>Events are sent oldest-first and one at a time; on the first failure the batch stops, so
 *       a later event for the same trade is never published ahead of an earlier one.</li>
 *   <li>While Kafka is unreachable the relay backs off exponentially (up to {@code max-backoff})
 *       instead of retrying every poll; events simply accumulate in the outbox table.</li>
 *   <li>If the process dies after Kafka acks but before the row is marked published, the event is
 *       re-sent on the next poll, so consumers must de-duplicate on the {@code eventId} header.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int batchSize;
    private final Duration sendTimeout;
    private final Duration maxBackoff;

    private Duration currentBackoff = Duration.ZERO;
    private Instant pausedUntil = Instant.MIN;

    public OutboxRelay(OutboxRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       TransactionTemplate tx,
                       Clock clock,
                       @Value("${app.outbox.relay.batch-size:100}") int batchSize,
                       @Value("${app.outbox.relay.send-timeout:20s}") Duration sendTimeout,
                       @Value("${app.outbox.relay.max-backoff:30s}") Duration maxBackoff) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.tx = tx;
        this.clock = clock;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
        this.maxBackoff = maxBackoff;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.poll-interval:500ms}")
    public void poll() {
        if (clock.instant().isBefore(pausedUntil)) {
            return;
        }
        BatchResult result;
        do {
            result = tx.execute(status -> relayBatch());
        } while (result != null && result.ok() && result.sent() == batchSize); // drain a backlog quickly
    }

    /** Runs inside a transaction that holds row locks on the batch. Visible for testing. */
    BatchResult relayBatch() {
        List<OutboxEvent> batch = repository.lockNextBatch(batchSize);
        int sent = 0;
        for (OutboxEvent event : batch) {
            try {
                send(event);
                event.markPublished();
                sent++;
            } catch (Exception e) {
                Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                event.markFailed(cause.getClass().getSimpleName() + ": " + cause.getMessage());
                onFailure(event, cause, batch.size() - sent);
                return new BatchResult(sent, false);
            }
        }
        if (sent > 0) {
            log.atInfo().addKeyValue("count", sent).log("Relayed outbox events to Kafka");
        }
        onSuccess();
        return new BatchResult(sent, true);
    }

    private void send(OutboxEvent event) throws Exception {
        var record = new ProducerRecord<>(event.getTopic(), event.getAggregateId(), event.getPayload());
        record.headers()
                .add("eventId", event.getId().toString().getBytes(StandardCharsets.UTF_8))
                .add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
        var md = kafkaTemplate.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS).getRecordMetadata();
        log.atDebug()
                .addKeyValue("eventId", event.getId())
                .addKeyValue("aggregateId", event.getAggregateId())
                .addKeyValue("topic", md.topic())
                .addKeyValue("partition", md.partition())
                .addKeyValue("offset", md.offset())
                .log("Published outbox event");
    }

    private void onFailure(OutboxEvent event, Throwable cause, int remaining) {
        currentBackoff = currentBackoff.isZero()
                ? INITIAL_BACKOFF
                : min(currentBackoff.multipliedBy(2), maxBackoff);
        pausedUntil = clock.instant().plus(currentBackoff);
        log.atWarn()
                .addKeyValue("eventId", event.getId())
                .addKeyValue("aggregateId", event.getAggregateId())
                .addKeyValue("attempts", event.getAttempts())
                .addKeyValue("pendingInBatch", remaining)
                .addKeyValue("retryInMs", currentBackoff.toMillis())
                .addKeyValue("error", cause.toString())
                .log("Kafka publish failed; event kept in outbox for retry");
    }

    private void onSuccess() {
        if (!currentBackoff.isZero()) {
            log.info("Kafka publishing recovered");
        }
        currentBackoff = Duration.ZERO;
        pausedUntil = Instant.MIN;
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    record BatchResult(int sent, boolean ok) {
    }
}
