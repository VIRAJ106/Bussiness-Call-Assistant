-- DayClose multi-tenancy row-level security (Flyway migration V4).
-- Implements ADR-0008: tenant_id everywhere plus row-level security.

ALTER TABLE locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE items ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_levels ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE reservations ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox ENABLE ROW LEVEL SECURITY;

-- Policy helper: returns current tenant UUID or null
CREATE OR REPLACE FUNCTION current_tenant_id() RETURNS uuid AS $$
BEGIN
  RETURN nullif(current_setting('app.current_tenant_id', true), '')::uuid;
EXCEPTION WHEN OTHERS THEN
  RETURN NULL;
END;
$$ LANGUAGE plpgsql STABLE;

-- RLS policies: allow access if app.current_tenant_id matches tenant_id, OR if setting is unset (system/migration context)
CREATE POLICY tenant_isolation_locations ON locations
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());

CREATE POLICY tenant_isolation_items ON items
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());

CREATE POLICY tenant_isolation_stock_levels ON stock_levels
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());

CREATE POLICY tenant_isolation_stock_movements ON stock_movements
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());

CREATE POLICY tenant_isolation_reservations ON reservations
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());

CREATE POLICY tenant_isolation_outbox ON outbox
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());
