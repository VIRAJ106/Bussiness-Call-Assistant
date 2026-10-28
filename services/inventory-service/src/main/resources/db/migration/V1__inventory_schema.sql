-- DayClose inventory-service schema (Flyway migration V1). PostgreSQL 14+.
-- tenant_id and commitment_id are plain UUIDs: tenants live in auth-service and
-- commitments in commitment-service (database per service, ADR-0001).

CREATE TABLE locations (
  id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  name      text NOT NULL,
  UNIQUE (tenant_id, name)
);

CREATE TABLE items (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     uuid NOT NULL,
  sku           text NOT NULL,
  name          text NOT NULL,
  unit          text NOT NULL DEFAULT 'pcs',
  price_paise   int  NOT NULL CHECK (price_paise >= 0),
  reorder_point int  NOT NULL DEFAULT 0 CHECK (reorder_point >= 0),
  reorder_qty   int  NOT NULL DEFAULT 0 CHECK (reorder_qty >= 0),
  active        boolean NOT NULL DEFAULT true,
  UNIQUE (tenant_id, sku)
);

-- THE correctness guarantee: stock can never be oversold, whatever the application does.
CREATE TABLE stock_levels (
  item_id     uuid NOT NULL REFERENCES items(id),
  location_id uuid NOT NULL REFERENCES locations(id),
  tenant_id   uuid NOT NULL,
  on_hand     int  NOT NULL DEFAULT 0 CHECK (on_hand  >= 0),
  reserved    int  NOT NULL DEFAULT 0 CHECK (reserved >= 0),
  PRIMARY KEY (item_id, location_id),
  CONSTRAINT no_oversell CHECK (reserved <= on_hand)
);

-- Append-only ledger: every change to stock is a row here.
CREATE TABLE stock_movements (
  id            bigserial PRIMARY KEY,
  tenant_id     uuid NOT NULL,
  item_id       uuid NOT NULL REFERENCES items(id),
  location_id   uuid NOT NULL REFERENCES locations(id),
  delta_on_hand int  NOT NULL DEFAULT 0,
  delta_reserved int NOT NULL DEFAULT 0,
  reason        text NOT NULL CHECK (reason IN ('receipt','adjustment','reserve','release','sale')),
  commitment_id uuid,
  actor         text NOT NULL DEFAULT 'system',   -- 'agent' | 'owner:<id>' | 'system'
  at            timestamptz NOT NULL DEFAULT now()
);

CREATE FUNCTION forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS
$$ BEGIN RAISE EXCEPTION 'stock_movements is append-only'; END $$;

CREATE TRIGGER stock_movements_immutable
  BEFORE UPDATE OR DELETE ON stock_movements
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- A reservation is a temporary claim on stock for a commitment (ADR-0005).
CREATE TABLE reservations (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     uuid NOT NULL,
  commitment_id uuid NOT NULL,
  item_id       uuid NOT NULL REFERENCES items(id),
  location_id   uuid NOT NULL REFERENCES locations(id),
  qty           int  NOT NULL CHECK (qty > 0),
  state         text NOT NULL DEFAULT 'held' CHECK (state IN ('held','committed','released')),
  expires_at    timestamptz NOT NULL,
  UNIQUE (commitment_id, item_id, location_id)     -- idempotent retries
);
CREATE INDEX reservations_expiry ON reservations (expires_at) WHERE state = 'held';

-- Receive stock (purchase receipt, count correction upward).
CREATE FUNCTION receive_stock(p_tenant uuid, p_item uuid, p_loc uuid, p_qty int, p_actor text DEFAULT 'system')
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO stock_levels (item_id, location_id, tenant_id, on_hand) VALUES (p_item, p_loc, p_tenant, p_qty)
  ON CONFLICT (item_id, location_id) DO UPDATE SET on_hand = stock_levels.on_hand + EXCLUDED.on_hand;
  INSERT INTO stock_movements (tenant_id, item_id, location_id, delta_on_hand, reason, actor)
  VALUES (p_tenant, p_item, p_loc, p_qty, 'receipt', p_actor);
END $$;

-- Release holds that have expired (also run by a background sweeper).
CREATE FUNCTION release_expired(p_item uuid, p_loc uuid) RETURNS int LANGUAGE plpgsql AS $$
DECLARE r record; n int := 0;
BEGIN
  FOR r IN SELECT * FROM reservations
            WHERE item_id = p_item AND location_id = p_loc AND state = 'held' AND expires_at <= now()
            FOR UPDATE SKIP LOCKED LOOP
    UPDATE reservations SET state = 'released' WHERE id = r.id;
    UPDATE stock_levels SET reserved = reserved - r.qty WHERE item_id = r.item_id AND location_id = r.location_id;
    INSERT INTO stock_movements (tenant_id, item_id, location_id, delta_reserved, reason, commitment_id)
    VALUES (r.tenant_id, r.item_id, r.location_id, -r.qty, 'release', r.commitment_id);
    n := n + 1;
  END LOOP;
  RETURN n;
END $$;

-- Atomically reserve p_qty. Raises 'insufficient_stock' (SQLSTATE IS001) if not enough is available.
-- The conditional UPDATE locks the stock row, so concurrent callers are serialized per item+location.
CREATE FUNCTION reserve_stock(p_tenant uuid, p_commitment uuid, p_item uuid, p_loc uuid,
                              p_qty int, p_ttl interval DEFAULT interval '30 minutes')
RETURNS uuid LANGUAGE plpgsql AS $$
DECLARE rid uuid;
BEGIN
  SELECT id INTO rid FROM reservations
   WHERE commitment_id = p_commitment AND item_id = p_item AND location_id = p_loc;
  IF FOUND THEN RETURN rid; END IF;                 -- retry of the same request

  PERFORM release_expired(p_item, p_loc);

  UPDATE stock_levels SET reserved = reserved + p_qty
   WHERE item_id = p_item AND location_id = p_loc AND tenant_id = p_tenant
     AND on_hand - reserved >= p_qty;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'insufficient_stock' USING ERRCODE = 'IS001';
  END IF;

  INSERT INTO reservations (tenant_id, commitment_id, item_id, location_id, qty, expires_at)
  VALUES (p_tenant, p_commitment, p_item, p_loc, p_qty, now() + p_ttl) RETURNING id INTO rid;
  INSERT INTO stock_movements (tenant_id, item_id, location_id, delta_reserved, reason, commitment_id)
  VALUES (p_tenant, p_item, p_loc, p_qty, 'reserve', p_commitment);
  RETURN rid;
END $$;

-- Owner approved: turn the hold into a sale (stock leaves).
CREATE FUNCTION commit_reservation(p_reservation uuid, p_actor text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE r reservations%ROWTYPE;
BEGIN
  SELECT * INTO r FROM reservations WHERE id = p_reservation AND state = 'held' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'reservation_not_held' USING ERRCODE = 'IS002'; END IF;
  UPDATE reservations SET state = 'committed' WHERE id = r.id;
  UPDATE stock_levels SET on_hand = on_hand - r.qty, reserved = reserved - r.qty
   WHERE item_id = r.item_id AND location_id = r.location_id;
  INSERT INTO stock_movements (tenant_id, item_id, location_id, delta_on_hand, delta_reserved, reason, commitment_id, actor)
  VALUES (r.tenant_id, r.item_id, r.location_id, -r.qty, -r.qty, 'sale', r.commitment_id, p_actor);
END $$;

-- Owner rejected, or the compensation path: give the stock back.
CREATE FUNCTION release_reservation(p_reservation uuid, p_actor text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE r reservations%ROWTYPE;
BEGIN
  SELECT * INTO r FROM reservations WHERE id = p_reservation AND state = 'held' FOR UPDATE;
  IF NOT FOUND THEN RETURN; END IF;                 -- already released or committed
  UPDATE reservations SET state = 'released' WHERE id = r.id;
  UPDATE stock_levels SET reserved = reserved - r.qty WHERE item_id = r.item_id AND location_id = r.location_id;
  INSERT INTO stock_movements (tenant_id, item_id, location_id, delta_reserved, reason, commitment_id, actor)
  VALUES (r.tenant_id, r.item_id, r.location_id, -r.qty, 'release', r.commitment_id, p_actor);
END $$;
