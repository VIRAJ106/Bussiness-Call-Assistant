-- DayClose voice-agent-service schema (Flyway migration V1). PostgreSQL 14+.
-- Implements ADR-0004 cascaded voice pipeline and ADR-0009 agent architecture.

-- Call sessions (one per phone call or chat)
CREATE TABLE call_sessions (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         uuid NOT NULL,
  caller_id         text,
  direction         text NOT NULL CHECK (direction IN ('inbound', 'outbound')),
  state             text NOT NULL DEFAULT 'initiated' CHECK (state IN (
    'initiated', 'active', 'tool_executing', 'completed', 'failed', 'abandoned'
  )),
  language          text NOT NULL DEFAULT 'en-IN',
  started_at        timestamptz NOT NULL DEFAULT now(),
  ended_at          timestamptz,
  duration_seconds  int,
  transcript_summary text,
  metadata          jsonb
);

CREATE INDEX idx_sessions_tenant_state ON call_sessions (tenant_id, state, started_at);
CREATE INDEX idx_sessions_caller ON call_sessions (tenant_id, caller_id, started_at);

-- Conversation turns (customer speaks, agent responds)
CREATE TABLE turns (
  id            bigserial PRIMARY KEY,
  session_id    uuid NOT NULL REFERENCES call_sessions(id) ON DELETE CASCADE,
  speaker       text NOT NULL CHECK (speaker IN ('customer', 'agent')),
  transcript    text NOT NULL,
  audio_url     text,
  duration_ms   int NOT NULL DEFAULT 0,
  occurred_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_turns_session ON turns (session_id, occurred_at);

-- Tool calls (agent invoking inventory, knowledge, etc.)
CREATE TABLE tool_calls (
  id            bigserial PRIMARY KEY,
  session_id    uuid NOT NULL REFERENCES call_sessions(id) ON DELETE CASCADE,
  tool_name     text NOT NULL,
  arguments     jsonb NOT NULL,
  result        jsonb,
  status        text NOT NULL CHECK (status IN ('success', 'failed')),
  duration_ms   int NOT NULL DEFAULT 0,
  occurred_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_tool_calls_session ON tool_calls (session_id, occurred_at);

-- Agent guardrail violations (numbers spoken incorrectly, policy violations)
CREATE TABLE guardrail_violations (
  id            bigserial PRIMARY KEY,
  session_id    uuid NOT NULL REFERENCES call_sessions(id) ON DELETE CASCADE,
  turn_id       bigint REFERENCES turns(id),
  rule_name     text NOT NULL,
  violation     text NOT NULL,
  severity      text NOT NULL CHECK (severity IN ('warning', 'error', 'critical')),
  corrected     boolean NOT NULL DEFAULT false,
  occurred_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_violations_session ON guardrail_violations (session_id, severity);

-- Outbox for event publishing
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

-- Row-level security for multi-tenancy
ALTER TABLE call_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE turns ENABLE ROW LEVEL SECURITY;
ALTER TABLE tool_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE guardrail_violations ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION current_tenant_id() RETURNS uuid AS $$
BEGIN
  RETURN nullif(current_setting('app.current_tenant_id', true), '')::uuid;
EXCEPTION WHEN OTHERS THEN
  RETURN NULL;
END;
$$ LANGUAGE plpgsql STABLE;

CREATE POLICY tenant_isolation_sessions ON call_sessions
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());

CREATE POLICY tenant_isolation_turns ON turns
  FOR ALL USING (
    current_tenant_id() IS NULL OR 
    session_id IN (SELECT id FROM call_sessions WHERE tenant_id = current_tenant_id())
  );

CREATE POLICY tenant_isolation_tool_calls ON tool_calls
  FOR ALL USING (
    current_tenant_id() IS NULL OR 
    session_id IN (SELECT id FROM call_sessions WHERE tenant_id = current_tenant_id())
  );

CREATE POLICY tenant_isolation_violations ON guardrail_violations
  FOR ALL USING (
    current_tenant_id() IS NULL OR 
    session_id IN (SELECT id FROM call_sessions WHERE tenant_id = current_tenant_id())
  );

CREATE POLICY tenant_isolation_outbox ON outbox
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());
