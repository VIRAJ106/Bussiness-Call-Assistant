-- DayClose notification-service schema (Flyway migration V1). PostgreSQL 14+.
-- Implements notification queue and delivery tracking.

-- Notification queue
CREATE TABLE notifications (
  id              bigserial PRIMARY KEY,
  tenant_id       uuid NOT NULL,
  recipient       text NOT NULL,  -- phone number, email, etc.
  channel         text NOT NULL CHECK (channel IN ('sms', 'email', 'whatsapp', 'push')),
  template_id     text NOT NULL,
  template_data   jsonb NOT NULL,
  message_body    text NOT NULL,
  status          text NOT NULL DEFAULT 'pending' CHECK (status IN (
    'pending', 'sending', 'sent', 'failed', 'cancelled'
  )),
  priority        int NOT NULL DEFAULT 5 CHECK (priority >= 1 AND priority <= 10),
  scheduled_at    timestamptz NOT NULL DEFAULT now(),
  sent_at         timestamptz,
  failed_reason   text,
  retry_count     int NOT NULL DEFAULT 0,
  max_retries     int NOT NULL DEFAULT 3,
  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_tenant_status ON notifications (tenant_id, status, scheduled_at);
CREATE INDEX idx_notifications_pending ON notifications (scheduled_at) 
  WHERE status = 'pending' AND scheduled_at <= now();

-- Notification templates
CREATE TABLE templates (
  id          text PRIMARY KEY,
  tenant_id   uuid,  -- NULL = system template
  channel     text NOT NULL CHECK (channel IN ('sms', 'email', 'whatsapp', 'push')),
  name        text NOT NULL,
  body        text NOT NULL,  -- Handlebars template
  variables   jsonb,          -- List of expected variables
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_templates_tenant_channel ON templates (tenant_id, channel);

-- Delivery tracking (provider-specific delivery status)
CREATE TABLE delivery_status (
  id                bigserial PRIMARY KEY,
  notification_id   bigint NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  provider          text NOT NULL,  -- 'twilio', 'sendgrid', etc.
  provider_id       text,           -- Provider's message ID
  status            text NOT NULL,
  status_detail     text,
  delivered_at      timestamptz,
  created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_status_notification ON delivery_status (notification_id);

-- Inbox for idempotent event handling
CREATE TABLE inbox (
  event_id    uuid PRIMARY KEY,
  event_type  text NOT NULL,
  tenant_id   uuid NOT NULL,
  processed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_inbox_tenant ON inbox (tenant_id, processed_at);

-- Row-level security
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE delivery_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE inbox ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION current_tenant_id() RETURNS uuid AS $$
BEGIN
  RETURN nullif(current_setting('app.current_tenant_id', true), '')::uuid;
EXCEPTION WHEN OTHERS THEN
  RETURN NULL;
END;
$$ LANGUAGE plpgsql STABLE;

CREATE POLICY tenant_isolation_notifications ON notifications
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());

CREATE POLICY tenant_isolation_templates ON templates
  FOR ALL USING (
    current_tenant_id() IS NULL OR 
    tenant_id = current_tenant_id() OR 
    tenant_id IS NULL  -- System templates accessible to all
  );

CREATE POLICY tenant_isolation_delivery ON delivery_status
  FOR ALL USING (
    current_tenant_id() IS NULL OR 
    notification_id IN (SELECT id FROM notifications WHERE tenant_id = current_tenant_id())
  );

CREATE POLICY tenant_isolation_inbox ON inbox
  FOR ALL USING (current_tenant_id() IS NULL OR tenant_id = current_tenant_id());

-- Insert system templates
INSERT INTO templates (id, tenant_id, channel, name, body, variables) VALUES
  ('commitment_approved', NULL, 'sms', 'Commitment Approved', 
   'Hi {{customer_name}}! Your order for {{item_count}} items (Total: ₹{{total}}) has been confirmed. Thank you!',
   '["customer_name", "item_count", "total"]'::jsonb),
  
  ('commitment_rejected', NULL, 'sms', 'Commitment Rejected',
   'Hi {{customer_name}}. Unfortunately, we cannot fulfill your order at this time. Reason: {{reason}}. Please call us for assistance.',
   '["customer_name", "reason"]'::jsonb),
  
  ('stock_low', NULL, 'sms', 'Low Stock Alert',
   'ALERT: {{item_name}} stock is low. Available: {{available}}, Reorder point: {{reorder_point}}. Please restock soon.',
   '["item_name", "available", "reorder_point"]'::jsonb),
  
  ('owner_approval_needed', NULL, 'whatsapp', 'Approval Needed',
   'New order awaiting approval:\nCustomer: {{customer_ref}}\nItems: {{item_count}}\nTotal: ₹{{total}}\n\nApprove: {{approve_link}}\nReject: {{reject_link}}',
   '["customer_ref", "item_count", "total", "approve_link", "reject_link"]'::jsonb);
