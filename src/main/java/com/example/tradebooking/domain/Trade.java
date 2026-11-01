package com.example.tradebooking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "trades")
public class Trade {

    /** Service-assigned UUIDv7. */
    @Id
    @Column(name = "trade_id", nullable = false, updatable = false)
    private UUID tradeId;

    @Column(nullable = false, length = 128)
    private String borrower;

    @Column(nullable = false, length = 128)
    private String lender;

    @Column(nullable = false, length = 16)
    private String security;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal rate;

    @Column(nullable = false, length = 8)
    private String term;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TradeStatus status;

    @Column(name = "booked_at", nullable = false, updatable = false)
    private Instant bookedAt;

    /** Null until first persisted; lets Spring Data treat an entity with an assigned id as new (no extra SELECT). */
    @Version
    private Long version;

    protected Trade() {
        // JPA
    }

    public Trade(UUID tradeId, String borrower, String lender, String security,
                 long quantity, BigDecimal rate, String term, LocalDate tradeDate) {
        this.tradeId = tradeId;
        this.borrower = borrower;
        this.lender = lender;
        this.security = security;
        this.quantity = quantity;
        this.rate = rate;
        this.term = term;
        this.tradeDate = tradeDate;
        this.status = TradeStatus.BOOKED;
    }

    @PrePersist
    void onCreate() {
        if (bookedAt == null) {
            bookedAt = Instant.now();
        }
    }

    public UUID getTradeId() { return tradeId; }
    public String getBorrower() { return borrower; }
    public String getLender() { return lender; }
    public String getSecurity() { return security; }
    public long getQuantity() { return quantity; }
    public BigDecimal getRate() { return rate; }
    public String getTerm() { return term; }
    public LocalDate getTradeDate() { return tradeDate; }
    public TradeStatus getStatus() { return status; }
    public Instant getBookedAt() { return bookedAt; }
}
