CREATE TABLE outbox_events (
    id              UUID          PRIMARY KEY,      -- UUIDv7; also the eventId consumers de-dupe on
    aggregate_type  VARCHAR(64)   NOT NULL,
    aggregate_id    VARCHAR(64)   NOT NULL,         -- Kafka message key
    event_type      VARCHAR(64)   NOT NULL,
    topic           VARCHAR(249)  NOT NULL,
    payload         TEXT          NOT NULL,         -- exact JSON bytes to publish
    created_at      TIMESTAMPTZ   NOT NULL,
    published_at    TIMESTAMPTZ,
    attempts        INT           NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000)
);

-- Small partial index: only unpublished rows, which is what the relay scans.
CREATE INDEX idx_outbox_pending ON outbox_events (created_at, id) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_published_at ON outbox_events (published_at) WHERE published_at IS NOT NULL;
