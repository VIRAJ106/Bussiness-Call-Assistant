-- DayClose commitment-service schema (Flyway migration V1). PostgreSQL 14+.
-- Implements ADR-0005 commitment state machine and ADR-0006 inbox pattern.

-- A commitment is a customer request (order, discount, reorder) that needs owner approval.
-- State machine: created → pending_approval → (approved | rejected) → (committed | failed)
CREATE TABLE commitments (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     uuid NOT NULL,
  customer_ref  text,
  type          text NOT NULL CHECK (type IN ('order', 'discount', 'reorder')),
  tier          int NOT NULL CHECK (tier IN (1, 2)),
  state         text NOT NULL DEFAULT 'created' CHECK (state IN (
    'created', 'pending_approval', 'approved', 'rejected', 'committed', 'failed'
  )),
  total_paise   int NOT NULL CHECK (total_paise >= 0),
  currency      char(3) NOT NULL DEFAULT 'INR',
  metadata      jsonb,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_commitments_tenant_state ON commitments (tenant_id, state, created_at);
CREATE INDEX idx_commitments_customer ON commitments (tenant_id, customer_ref, created_at);

-- Commitment lines (items being ordered/requested)
CREATE TABLE commitment_lines (
  id            bigserial PRIMARY KEY,
  commitment_id uuid NOT NULL REFERENCES commitments(id) ON DELETE CASCADE,
  item_id       uuid NOT NULL,
  location_id   uuid NOT NULL,
  reservation_id uuid,  -- Link to inventory-service reservation (no FK, different DB)
  qty           int NOT NULL CHECK (qty > 0),
  price_paise   int NOT NULL CHECK (price_paise >= 0)
);

CREATE INDEX idx_commitment_lines_commitment ON commitment_lines (commitment_id);

-- Event sourcing: every state transition is recorded (audit trail + replay)
CREATE TABLE commitment_events (
  id              bigserial PRIMARY KEY,
  commitment_id   uuid NOT NULL REFERENCES commitments(id) ON DELETE CASCADE,
  event_type      text NOT NULL,
  from_state      text,
  to_state        text,
  actor           text NOT NULL,  -- 'agent', 'owner:<id>', 'system'
  reason          text,
  occurred_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_commitment_events_commitment ON commitment_events (commitment_id, occurred_at);

-- Policy rules for auto-approval (Tier 1 vs Tier 2 decision)
CREATE TABLE policy_rules (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     uuid NOT NULL,
  name          text NOT NULL,
  priority      int NOT NULL DEFAULT 100,
  enabled       boolean NOT NULL DEFAULT true,
  conditions    jsonb NOT NULL,  -- e.g., {"max_total_paise": 50000, "customer_type": "trusted"}
  action        text NOT NULL CHECK (action IN ('auto_approve', 'require_approval')),
  created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_policy_rules_tenant ON policy_rules (tenant_id, priority DESC);

-- Inbox for idempotent event handling (ADR-0006)
CREATE TABLE inbox (
  event_id    uuid PRIMARY KEY,
  event_type  text NOT NULL,
  tenant_id   uuid NOT NULL,
  processed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_inbox_tenant ON inbox (tenant_id, processed_at);

-- Outbox for publishing events (same pattern as inventory-service)
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

-- Row-level security for multi-tenancy (ADR-0008)
ALTER TABLE commitments ENABLE ROW LEVEL SECURITY;
ALTER TABLE commitment_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE commitment_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE policy_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE inbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION current_tenant_id() RETURNS uuid AS $$
BEGIN
  RETURN nullif(current_setting('app.current_tenant_id', true), '')::uuid;
EXCEPTION WHEN OTHERS THEN
  RETURN NULL;
END;
$$ LANGUAGE plpgsql STABLE;

CREATE POLICY tenant_isolation_commitments ON commitments
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());

CREATE POLICY tenant_isolation_commitment_lines ON commitment_lines
  FOR ALL USING (
    current_tenant_id() IS NULL OR 
    commitment_id IN (SELECT id FROM commitments WHERE tenant_id = current_tenant_id())
  );

CREATE POLICY tenant_isolation_commitment_events ON commitment_events
  FOR ALL USING (
    current_tenant_id() IS NULL OR 
    commitment_id IN (SELECT id FROM commitments WHERE tenant_id = current_tenant_id())
  );

CREATE POLICY tenant_isolation_policy_rules ON policy_rules
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());

CREATE POLICY tenant_isolation_inbox ON inbox
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());

CREATE POLICY tenant_isolation_outbox ON outbox
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());

-- Function: Transition commitment to a new state with event recording
CREATE OR REPLACE FUNCTION transition_commitment(
    p_commitment_id uuid,
    p_to_state text,
    p_actor text,
    p_reason text DEFAULT NULL
)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  v_from_state text;
BEGIN
  -- Get current state with row lock
  SELECT state INTO v_from_state FROM commitments
   WHERE id = p_commitment_id FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'commitment_not_found' USING ERRCODE = 'CM001';
  END IF;

  -- Validate state transition (simplified state machine)
  -- created → pending_approval → approved/rejected → committed/failed
  IF v_from_state = p_to_state THEN
    RETURN; -- Already in target state (idempotent)
  END IF;

  -- Update state
  UPDATE commitments 
     SET state = p_to_state, updated_at = now()
   WHERE id = p_commitment_id;

  -- Record event
  INSERT INTO commitment_events (commitment_id, event_type, from_state, to_state, actor, reason)
  VALUES (p_commitment_id, 'state_transition', v_from_state, p_to_state, p_actor, p_reason);
END $$;
