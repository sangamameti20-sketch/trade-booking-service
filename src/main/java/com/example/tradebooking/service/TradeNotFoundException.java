package com.example.tradebooking.service;

public class TradeNotFoundException extends RuntimeException {

    public TradeNotFoundException(java.util.UUID tradeId) {
        super("Trade with tradeId '" + tradeId + "' not found");
    }
}
