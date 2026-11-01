package com.example.tradebooking.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    OutboxRepository repository;

    @Mock
    KafkaTemplate<String, String> kafka;

    @Mock
    TransactionTemplate tx;

    MutableClock clock = new MutableClock(Instant.parse("2026-11-01T10:00:00Z"));
    OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, kafka, tx, clock, 100, Duration.ofSeconds(1), Duration.ofSeconds(30));
        lenient().when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
    }

    private static OutboxEvent event(String tradeId) {
        return new OutboxEvent(UUID.randomUUID(), "Trade", tradeId, "TradeBooked", "trade-events", "{\"tradeId\":\"" + tradeId + "\"}");
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<SendResult<String, String>> ok() {
        SendResult<String, String> result = mock(SendResult.class);
        when(result.getRecordMetadata()).thenReturn(new RecordMetadata(new TopicPartition("trade-events", 0), 0, 0, 0, 0, 0));
        return CompletableFuture.completedFuture(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesKeyedEventsWithHeadersAndMarksThemPublished() {
        var e1 = event("t1");
        var e2 = event("t2");
        when(repository.lockNextBatch(anyInt())).thenReturn(List.of(e1, e2));
        var okFuture1 = ok();
        var okFuture2 = ok();
        when(kafka.send(any(ProducerRecord.class))).thenReturn(okFuture1, okFuture2);

        relay.poll();

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka, times(2)).send(sent.capture());
        assertThat(sent.getAllValues()).extracting(ProducerRecord::key).containsExactly("t1", "t2");
        assertThat(new String(sent.getAllValues().get(0).headers().lastHeader("eventId").value()))
                .isEqualTo(e1.getId().toString());
        assertThat(e1.getPublishedAt()).isNotNull();
        assertThat(e2.getPublishedAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsEventsAndStopsBatchWhenKafkaIsDown() {
        var e1 = event("t1");
        var e2 = event("t2");
        when(repository.lockNextBatch(anyInt())).thenReturn(List.of(e1, e2));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker unavailable")));

        relay.poll();

        verify(kafka, times(1)).send(any(ProducerRecord.class)); // e2 not sent ahead of e1
        assertThat(e1.getPublishedAt()).isNull();
        assertThat(e1.getAttempts()).isEqualTo(1);
        assertThat(e1.getLastError()).contains("broker unavailable");
        assertThat(e2.getPublishedAt()).isNull();
        assertThat(e2.getAttempts()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void backsOffAfterFailureAndResumesWhenKafkaRecovers() {
        var e1 = event("t1");
        when(repository.lockNextBatch(anyInt())).thenReturn(List.of(e1));
        var okFuture = ok();
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("down")), okFuture);

        relay.poll();                       // fails -> pause 1s
        relay.poll();                       // still paused: no DB access
        verify(repository, times(1)).lockNextBatch(anyInt());

        clock.advance(Duration.ofMillis(1001));
        relay.poll();                       // retried and succeeds

        verify(repository, times(2)).lockNextBatch(anyInt());
        assertThat(e1.getPublishedAt()).isNotNull();
        assertThat(e1.getAttempts()).isEqualTo(2);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
