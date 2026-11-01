package com.example.tradebooking.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TradeResponse(
        UUID tradeId,
        String borrower,
        String lender,
        String security,
        long quantity,
        BigDecimal rate,
        String term,
        LocalDate tradeDate,
        String status,
        Instant bookedAt
) {
}
