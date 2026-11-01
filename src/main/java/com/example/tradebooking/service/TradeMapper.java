package com.example.tradebooking.service;

import com.example.tradebooking.api.dto.BookTradeRequest;
import com.example.tradebooking.api.dto.TradeResponse;
import com.example.tradebooking.domain.Trade;
import com.example.tradebooking.event.TradeBookedEvent;

import java.time.Instant;
import java.util.UUID;

final class TradeMapper {

    private TradeMapper() {
    }

    static Trade toEntity(UUID tradeId, BookTradeRequest r) {
        return new Trade(tradeId, r.borrower().trim(), r.lender().trim(), r.security().trim(),
                r.quantity(), r.rate(), r.term(), r.tradeDate());
    }

    static TradeResponse toResponse(Trade t) {
        return new TradeResponse(t.getTradeId(), t.getBorrower(), t.getLender(), t.getSecurity(),
                t.getQuantity(), t.getRate(), t.getTerm(), t.getTradeDate(),
                t.getStatus().name(), t.getBookedAt());
    }

    static TradeBookedEvent toEvent(UUID eventId, Trade t) {
        return new TradeBookedEvent(eventId, TradeBookedEvent.TYPE, Instant.now(),
                t.getTradeId(), t.getBorrower(), t.getLender(), t.getSecurity(),
                t.getQuantity(), t.getRate(), t.getTerm(), t.getTradeDate());
    }
}
