-- DayClose transactional outbox table (Flyway migration V3).
-- Implements ADR-0006: Transactional outbox + RabbitMQ relay.

CREATE TABLE outbox (
  id             bigserial PRIMARY KEY,
  event_id       uuid NOT NULL UNIQUE,
  event_type     text NOT NULL,
  version        int NOT NULL DEFAULT 1,
  tenant_id      uuid NOT NULL,
  correlation_id uuid,
  causation_id   uuid,
  payload        jsonb NOT NULL,
  occurred_at    timestamptz NOT NULL DEFAULT now(),
  published      boolean NOT NULL DEFAULT false,
  published_at   timestamptz,
  retry_count    int NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_unpublished ON outbox (id) WHERE NOT published;
