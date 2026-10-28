-- DayClose tenant settings and abuse limits (Flyway migration V5).
-- Implements ADR-0005 abuse limits and per-tenant caps.

CREATE TABLE tenant_settings (
  tenant_id                           uuid PRIMARY KEY,
  max_reservation_qty                 int NOT NULL DEFAULT 50 CHECK (max_reservation_qty > 0),
  max_active_reservations_per_customer int NOT NULL DEFAULT 5 CHECK (max_active_reservations_per_customer > 0),
  max_active_reservations_tenant      int NOT NULL DEFAULT 100 CHECK (max_active_reservations_tenant > 0),
  default_reservation_ttl_minutes     int NOT NULL DEFAULT 30 CHECK (default_reservation_ttl_minutes > 0),
  max_reservation_ttl_minutes         int NOT NULL DEFAULT 120 CHECK (max_reservation_ttl_minutes > 0),
  created_at                          timestamptz NOT NULL DEFAULT now(),
  updated_at                          timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE tenant_settings ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_settings ON tenant_settings
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());

-- Update reserve_stock to enforce abuse limits
CREATE OR REPLACE FUNCTION reserve_stock(
    p_tenant uuid,
    p_commitment uuid,
    p_item uuid,
    p_loc uuid,
    p_qty int,
    p_ttl interval DEFAULT interval '30 minutes',
    p_customer_ref text DEFAULT NULL
)
RETURNS uuid LANGUAGE plpgsql AS $$
DECLARE
  r record;
  rid uuid;
  ts tenant_settings%ROWTYPE;
  customer_held_count int;
  tenant_held_count int;
BEGIN
  -- Finding 4: Guard quantity
  IF p_qty <= 0 THEN
    RAISE EXCEPTION 'invalid_quantity: quantity must be positive' USING ERRCODE = 'IS006';
  END IF;

  -- Load tenant settings or defaults
  SELECT * INTO ts FROM tenant_settings WHERE tenant_id = p_tenant;
  IF FOUND THEN
    IF p_qty > ts.max_reservation_qty THEN
      RAISE EXCEPTION 'reservation_limit_exceeded: quantity % exceeds maximum %', p_qty, ts.max_reservation_qty USING ERRCODE = 'IS008';
    END IF;
  END IF;

  -- Finding 2: Transaction-scoped advisory lock prevents race on concurrent identical idempotency keys
  PERFORM pg_advisory_xact_lock(hashtextextended(p_commitment::text || p_item::text || p_loc::text, 0));

  -- Check existing reservation state for idempotency
  SELECT id, qty, state INTO r FROM reservations
   WHERE commitment_id = p_commitment AND item_id = p_item AND location_id = p_loc;

  IF FOUND THEN
    IF r.qty != p_qty THEN
      RAISE EXCEPTION 'idempotency_conflict: reservation exists with different quantity' USING ERRCODE = 'IS004';
    END IF;
    IF r.state = 'held' THEN
      RETURN r.id;
    ELSE
      RAISE EXCEPTION 'reservation_closed: reservation is already %', r.state USING ERRCODE = 'IS003';
    END IF;
  END IF;

  -- Enforce per-customer open reservation limits if customer_ref is provided
  IF p_customer_ref IS NOT NULL AND ts.max_active_reservations_per_customer IS NOT NULL THEN
    PERFORM pg_advisory_xact_lock(hashtextextended('customer_cap:' || p_tenant::text || ':' || p_customer_ref, 0));
    SELECT count(*) INTO customer_held_count
      FROM reservations
     WHERE tenant_id = p_tenant AND customer_ref = p_customer_ref AND state = 'held' AND expires_at > now();

    IF customer_held_count >= ts.max_active_reservations_per_customer THEN
      RAISE EXCEPTION 'customer_reservation_cap_exceeded: maximum open holds reached' USING ERRCODE = 'IS009';
    END IF;
  END IF;

  -- Release expired holds for this item and location
  PERFORM release_expired(p_item, p_loc);

  -- Atomic conditional update: locks the stock row and enforces availability
  UPDATE stock_levels SET reserved = reserved + p_qty
   WHERE item_id = p_item AND location_id = p_loc AND tenant_id = p_tenant
     AND on_hand - reserved >= p_qty;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'insufficient_stock' USING ERRCODE = 'IS001';
  END IF;

  -- Record reservation
  INSERT INTO reservations (tenant_id, commitment_id, item_id, location_id, qty, customer_ref, expires_at)
  VALUES (p_tenant, p_commitment, p_item, p_loc, p_qty, p_customer_ref, now() + p_ttl)
  RETURNING id INTO rid;

  INSERT INTO stock_movements (tenant_id, item_id, location_id, delta_reserved, reason, commitment_id, actor)
  VALUES (p_tenant, p_item, p_loc, p_qty, 'reserve', p_commitment, 'agent');

  RETURN rid;
END $$;
