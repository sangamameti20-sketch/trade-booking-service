package com.example.tradebooking.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload published to the {@code trade-events} topic, keyed by tradeId. Delivery is
 * at-least-once (via the outbox), so consumers should de-duplicate on eventId.
 */
public record TradeBookedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID tradeId,
        String borrower,
        String lender,
        String security,
        long quantity,
        BigDecimal rate,
        String term,
        LocalDate tradeDate
) {
    public static final String TYPE = "TradeBooked";
}
