-- DayClose inventory-service schema hardening (Flyway migration V2).
-- Addresses findings 1-9, 11 from ADR review (§3 of Implementation Plan).

-- 11. Add currency column to items (Finding 11)
ALTER TABLE items ADD COLUMN currency char(3) NOT NULL DEFAULT 'INR';

-- 6. Add customer_ref to reservations (Finding 6)
ALTER TABLE reservations ADD COLUMN customer_ref text;

-- 4. Guard against non-positive quantity in receive_stock (Finding 4)
CREATE OR REPLACE FUNCTION receive_stock(p_tenant uuid, p_item uuid, p_loc uuid, p_qty int, p_actor text DEFAULT 'system')
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  IF p_qty <= 0 THEN
    RAISE EXCEPTION 'invalid_quantity: quantity must be positive' USING ERRCODE = 'IS006';
  END IF;

  INSERT INTO stock_levels (item_id, location_id, tenant_id, on_hand)
  VALUES (p_item, p_loc, p_tenant, p_qty)
  ON CONFLICT (item_id, location_id) DO UPDATE SET on_hand = stock_levels.on_hand + EXCLUDED.on_hand;

  INSERT INTO stock_movements (tenant_id, item_id, location_id, delta_on_hand, reason, actor)
  VALUES (p_tenant, p_item, p_loc, p_qty, 'receipt', p_actor);
END $$;

-- 1, 2, 4, 6. Hardened reserve_stock (Findings 1, 2, 4, 6)
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
BEGIN
  -- Finding 4: Guard quantity
  IF p_qty <= 0 THEN
    RAISE EXCEPTION 'invalid_quantity: quantity must be positive' USING ERRCODE = 'IS006';
  END IF;

  -- Finding 2: Transaction-scoped advisory lock prevents race on concurrent identical idempotency keys
  PERFORM pg_advisory_xact_lock(hashtextextended(p_commitment::text || p_item::text || p_loc::text, 0));

  -- Finding 1: Check existing reservation state for idempotency
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

  -- Release expired holds for this item and location
  PERFORM release_expired(p_item, p_loc);

  -- Atomic conditional update: locks the row and enforces availability
  UPDATE stock_levels SET reserved = reserved + p_qty
   WHERE item_id = p_item AND location_id = p_loc AND tenant_id = p_tenant
     AND on_hand - reserved >= p_qty;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'insufficient_stock' USING ERRCODE = 'IS001';
  END IF;

  -- Record reservation with customer reference
  INSERT INTO reservations (tenant_id, commitment_id, item_id, location_id, qty, customer_ref, expires_at)
  VALUES (p_tenant, p_commitment, p_item, p_loc, p_qty, p_customer_ref, now() + p_ttl)
  RETURNING id INTO rid;

  INSERT INTO stock_movements (tenant_id, item_id, location_id, delta_reserved, reason, commitment_id, actor)
  VALUES (p_tenant, p_item, p_loc, p_qty, 'reserve', p_commitment, 'agent');

  RETURN rid;
END $$;

-- 3, 7. Hardened commit_reservation with tenant check & expiry check (Findings 3, 7)
CREATE OR REPLACE FUNCTION commit_reservation(p_tenant uuid, p_reservation uuid, p_actor text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  r reservations%ROWTYPE;
BEGIN
  SELECT * INTO r FROM reservations
   WHERE id = p_reservation AND tenant_id = p_tenant AND state = 'held'
   FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'reservation_not_held' USING ERRCODE = 'IS002';
  END IF;

  IF r.expires_at <= now() THEN
    RAISE EXCEPTION 'reservation_expired' USING ERRCODE = 'IS005';
  END IF;

  UPDATE reservations SET state = 'committed' WHERE id = r.id;

  UPDATE stock_levels SET on_hand = on_hand - r.qty, reserved = reserved - r.qty
   WHERE item_id = r.item_id AND location_id = r.location_id;

  INSERT INTO stock_movements (tenant_id, item_id, location_id, delta_on_hand, delta_reserved, reason, commitment_id, actor)
  VALUES (r.tenant_id, r.item_id, r.location_id, -r.qty, -r.qty, 'sale', r.commitment_id, p_actor);
END $$;

-- 7. Hardened release_reservation with tenant check (Finding 7)
CREATE OR REPLACE FUNCTION release_reservation(p_tenant uuid, p_reservation uuid, p_actor text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  r reservations%ROWTYPE;
BEGIN
  SELECT * INTO r FROM reservations
   WHERE id = p_reservation AND tenant_id = p_tenant AND state = 'held'
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN; -- Already released or committed (idempotent)
  END IF;

  UPDATE reservations SET state = 'released' WHERE id = r.id;

  UPDATE stock_levels SET reserved = reserved - r.qty
   WHERE item_id = r.item_id AND location_id = r.location_id;

  INSERT INTO stock_movements (tenant_id, item_id, location_id, delta_reserved, reason, commitment_id, actor)
  VALUES (r.tenant_id, r.item_id, r.location_id, -r.qty, 'release', r.commitment_id, p_actor);
END $$;

-- 3. Extend reservation function for owner approval path (Finding 3)
CREATE OR REPLACE FUNCTION extend_reservation(p_tenant uuid, p_reservation uuid, p_extension interval)
RETURNS timestamptz LANGUAGE plpgsql AS $$
DECLARE
  r reservations%ROWTYPE;
  new_expiry timestamptz;
BEGIN
  SELECT * INTO r FROM reservations
   WHERE id = p_reservation AND tenant_id = p_tenant AND state = 'held'
   FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'reservation_not_held' USING ERRCODE = 'IS002';
  END IF;

  new_expiry := now() + p_extension;
  UPDATE reservations SET expires_at = new_expiry WHERE id = r.id;
  RETURN new_expiry;
END $$;

-- 5. Stock adjustment function (Finding 5)
CREATE OR REPLACE FUNCTION adjust_stock(
    p_tenant uuid,
    p_item uuid,
    p_loc uuid,
    p_delta int,
    p_actor text,
    p_reason_note text DEFAULT NULL
)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  IF p_delta = 0 THEN
    RETURN;
  END IF;

  -- Must not cause on_hand to drop below reserved
  UPDATE stock_levels
     SET on_hand = on_hand + p_delta
   WHERE item_id = p_item AND location_id = p_loc AND tenant_id = p_tenant
     AND on_hand + p_delta >= reserved;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'invalid_adjustment: on_hand cannot be less than reserved' USING ERRCODE = 'IS007';
  END IF;

  INSERT INTO stock_movements (tenant_id, item_id, location_id, delta_on_hand, reason, actor)
  VALUES (p_tenant, p_item, p_loc, p_delta, 'adjustment', p_actor);
END $$;

-- 5. Global sweeper function across all expired reservations (Finding 5)
CREATE OR REPLACE FUNCTION release_expired_all(p_batch_size int DEFAULT 100)
RETURNS int LANGUAGE plpgsql AS $$
DECLARE
  r record;
  n int := 0;
BEGIN
  FOR r IN
    SELECT id, tenant_id, item_id, location_id, qty, commitment_id
      FROM reservations
     WHERE state = 'held' AND expires_at <= now()
     LIMIT p_batch_size
       FOR UPDATE SKIP LOCKED
  LOOP
    UPDATE reservations SET state = 'released' WHERE id = r.id;
    UPDATE stock_levels SET reserved = reserved - r.qty
     WHERE item_id = r.item_id AND location_id = r.location_id;

    INSERT INTO stock_movements (tenant_id, item_id, location_id, delta_reserved, reason, commitment_id, actor)
    VALUES (r.tenant_id, r.item_id, r.location_id, -r.qty, 'release', r.commitment_id, 'sweeper');

    n := n + 1;
  END LOOP;
  RETURN n;
END $$;

-- 8. Multi-line reservation function sorted by (item_id, location_id) to prevent deadlocks (Finding 8)
-- p_lines: JSON array of objects [{"item_id": "uuid", "location_id": "uuid", "qty": int}]
CREATE OR REPLACE FUNCTION reserve_stock_lines(
    p_tenant uuid,
    p_commitment uuid,
    p_customer_ref text,
    p_lines jsonb,
    p_ttl interval DEFAULT interval '30 minutes'
)
RETURNS jsonb LANGUAGE plpgsql AS $$
DECLARE
  line record;
  res_id uuid;
  results jsonb := '[]'::jsonb;
BEGIN
  -- Iterate sorted by item_id, location_id to guarantee deterministic lock acquisition
  FOR line IN
    SELECT (elem->>'item_id')::uuid AS item_id,
           (elem->>'location_id')::uuid AS location_id,
           (elem->>'qty')::int AS qty
      FROM jsonb_array_elements(p_lines) AS elem
     ORDER BY (elem->>'item_id')::uuid, (elem->>'location_id')::uuid
  LOOP
    res_id := reserve_stock(p_tenant, p_commitment, line.item_id, line.location_id, line.qty, p_ttl, p_customer_ref);
    results := results || jsonb_build_object(
      'item_id', line.item_id,
      'location_id', line.location_id,
      'reservation_id', res_id,
      'qty', line.qty
    );
  END LOOP;

  RETURN results;
END $$;
