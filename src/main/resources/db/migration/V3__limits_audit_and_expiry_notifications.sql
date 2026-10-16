-- Plan entitlements and subscription-specific Admin overrides.
ALTER TABLE subscription_plans
    ADD COLUMN max_products INTEGER NOT NULL DEFAULT 100,
    ADD COLUMN max_custom_templates INTEGER NOT NULL DEFAULT 3;
ALTER TABLE subscription_plans
    ADD CONSTRAINT ck_subscription_plans_product_limit CHECK (max_products > 0),
    ADD CONSTRAINT ck_subscription_plans_template_limit CHECK (max_custom_templates >= 0);

ALTER TABLE customer_subscriptions
    ADD COLUMN invoice_limit_override INTEGER,
    ADD COLUMN product_limit_override INTEGER,
    ADD COLUMN custom_template_limit_override INTEGER,
    ADD COLUMN invoice_usage_carry_forward INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_subscription_invoice_override CHECK (invoice_limit_override IS NULL OR invoice_limit_override > 0),
    ADD CONSTRAINT ck_subscription_product_override CHECK (product_limit_override IS NULL OR product_limit_override > 0),
    ADD CONSTRAINT ck_subscription_template_override CHECK (custom_template_limit_override IS NULL OR custom_template_limit_override >= 0),
    ADD CONSTRAINT ck_subscription_usage_carry_forward CHECK (invoice_usage_carry_forward >= 0);

-- Invoices can be cancelled but cancellation never removes a recorded quota usage.
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_status_check;
ALTER TABLE invoices ADD CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT', 'SENT', 'PAID', 'CANCELLED'));
ALTER TABLE invoices ADD COLUMN discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0);
ALTER TABLE invoices ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE;

-- Append-only audit trail. Details are allow-listed summaries, never credential or payment data.
CREATE TABLE audit_records (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    actor VARCHAR(190) NOT NULL,
    action VARCHAR(48) NOT NULL,
    resource_type VARCHAR(48) NOT NULL,
    resource_name VARCHAR(160),
    resource_id VARCHAR(80),
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    summary VARCHAR(1000) NOT NULL,
    before_values TEXT,
    after_values TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_records_customer_time ON audit_records(customer_id, created_at DESC);
CREATE INDEX idx_audit_records_time ON audit_records(created_at DESC);
CREATE INDEX idx_audit_records_action_time ON audit_records(action, created_at DESC);
CREATE INDEX idx_audit_records_actor_time ON audit_records(actor, created_at DESC);

CREATE FUNCTION deny_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit records are append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_audit_records_append_only
    BEFORE UPDATE OR DELETE ON audit_records
    FOR EACH ROW EXECUTE FUNCTION deny_audit_mutation();

-- In-app expiry reminders, deduplicated per subscription/reminder offset.
CREATE TABLE subscription_notification_logs (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES customer_subscriptions(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    notification_type VARCHAR(24) NOT NULL CHECK (notification_type IN ('EXPIRING', 'EXPIRED')),
    days_before INTEGER NOT NULL DEFAULT 0 CHECK (days_before >= 0),
    message VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DELIVERED', 'READ', 'FAILED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_subscription_notification UNIQUE (subscription_id, notification_type, days_before)
);
CREATE INDEX idx_subscription_notifications_customer ON subscription_notification_logs(customer_id, created_at DESC);
CREATE INDEX idx_subscription_notifications_pending ON subscription_notification_logs(status, created_at);
