CREATE TABLE trades (
    trade_id    UUID           PRIMARY KEY,         -- service-generated UUIDv7
    borrower    VARCHAR(128)   NOT NULL,
    lender      VARCHAR(128)   NOT NULL,
    security    VARCHAR(16)    NOT NULL,
    quantity    BIGINT         NOT NULL CHECK (quantity > 0),
    rate        NUMERIC(12, 6) NOT NULL CHECK (rate > 0),
    term        VARCHAR(8)     NOT NULL,
    trade_date  DATE           NOT NULL,
    status      VARCHAR(16)    NOT NULL,
    booked_at   TIMESTAMPTZ    NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_trades_security_trade_date ON trades (security, trade_date);
