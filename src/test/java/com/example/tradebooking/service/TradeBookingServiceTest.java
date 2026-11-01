package com.example.tradebooking.service;

import com.example.tradebooking.api.dto.BookTradeRequest;
import com.example.tradebooking.config.IdGenerator;
import com.example.tradebooking.config.KafkaTopicConfig.TradeEventsTopic;
import com.example.tradebooking.domain.Trade;
import com.example.tradebooking.event.TradeBookedEvent;
import com.example.tradebooking.outbox.OutboxEvent;
import com.example.tradebooking.outbox.OutboxWriter;
import com.example.tradebooking.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeBookingServiceTest {

    @Mock
    TradeRepository repository;

    @Mock
    OutboxWriter outbox;

    TradeBookingService service;

    private final BookTradeRequest request = new BookTradeRequest("Goldman Sachs", "BlackRock",
            "AAPL", 10_000L, new BigDecimal("0.25"), "30D", LocalDate.parse("2026-11-01"));

    @BeforeEach
    void setUp() {
        service = new TradeBookingService(repository, outbox, new IdGenerator(), new TradeEventsTopic("trade-events"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void assignsUuidV7AndWritesTradeBookedEventToOutbox() {
        when(repository.save(any(Trade.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outbox.append(any(), any(), any(), any(), any())).thenReturn(
                new OutboxEvent(UUID.randomUUID(), "Trade", "x", "TradeBooked", "trade-events", "{}"));

        var response = service.book(request);

        assertThat(response.tradeId().version()).isEqualTo(7);
        assertThat(response.status()).isEqualTo("BOOKED");

        ArgumentCaptor<Function<UUID, TradeBookedEvent>> factory = ArgumentCaptor.forClass(Function.class);
        verify(outbox).append(eq("trade-events"), eq("Trade"), eq(response.tradeId().toString()),
                eq("TradeBooked"), factory.capture());

        UUID eventId = UUID.randomUUID();
        TradeBookedEvent event = factory.getValue().apply(eventId);
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.tradeId()).isEqualTo(response.tradeId());
        assertThat(event.quantity()).isEqualTo(10_000L);
        assertThat(event.rate()).isEqualByComparingTo("0.25");
    }

    @Test
    void generatedIdsAreUniqueAndTimeOrdered() {
        IdGenerator ids = new IdGenerator();
        UUID a = ids.newId();
        UUID b = ids.newId();
        assertThat(a).isNotEqualTo(b);
        assertThat(a.version()).isEqualTo(7);
        assertThat(a.toString().compareTo(b.toString())).isNegative();
    }
}
