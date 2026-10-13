-- BillGen V8 — Super Admin control plane, part 2: audit definitions, template versioning and
-- dynamic notifications.
--   * audit *definitions* (what is tracked, its quota and retention) are kept separate from the
--     immutable audit *history* rows written since V3
--   * bill templates become versioned, soft-deletable documents with a configurable preview
--   * notification content moves out of code and into administrator-managed records

-- --------------------------------------------------------------------------------------
-- 1. Audit type registry
-- --------------------------------------------------------------------------------------
CREATE TABLE audit_types (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    event_category VARCHAR(32) NOT NULL CHECK (event_category IN
        ('SECURITY', 'ACCOUNT', 'BILLING', 'CATALOG', 'SUBSCRIPTION', 'TEMPLATE', 'ADMINISTRATION', 'SYSTEM')),
    action VARCHAR(48) NOT NULL,
    resource_type VARCHAR(48),
    limit_period VARCHAR(16) NOT NULL DEFAULT 'NONE' CHECK (limit_period IN
        ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY', 'CUSTOM')),
    usage_limit INTEGER CHECK (usage_limit IS NULL OR usage_limit > 0),
    custom_period_days INTEGER CHECK (custom_period_days IS NULL OR custom_period_days BETWEEN 1 AND 365),
    retention_days INTEGER NOT NULL DEFAULT 730 CHECK (retention_days >= 1),
    alert_threshold_percent INTEGER NOT NULL DEFAULT 80
        CHECK (alert_threshold_percent BETWEEN 1 AND 100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    system_defined BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(190),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_audit_types_code UNIQUE (code),
    CONSTRAINT uq_audit_types_name UNIQUE (name),
    CONSTRAINT ck_audit_types_custom_period CHECK (limit_period <> 'CUSTOM' OR custom_period_days IS NOT NULL)
);
CREATE INDEX idx_audit_types_active ON audit_types(active, event_category, name);

INSERT INTO audit_types (code, name, description, event_category, action, resource_type, limit_period, retention_days, system_defined) VALUES
    ('AUTH_SESSION',   'Login and logout',            'Authentication events recorded for every sign-in and sign-out.', 'SECURITY',       'LOGIN',           NULL,                 'NONE',   1095, TRUE),
    ('AUTH_FAILURE',   'Failed sign-in attempts',     'Rejected credentials and disabled-account attempts.',            'SECURITY',       'LOGIN_FAILURE',   NULL,                 'NONE',   1095, TRUE),
    ('CUSTOMER_CHANGE','Customer creation or update', 'Administrator changes to a company account.',                    'ACCOUNT',        NULL,              'CUSTOMER',           'NONE',   1825, TRUE),
    ('CATALOG_CHANGE', 'Product creation or update',  'Changes to an account''s product catalogue.',                    'CATALOG',        NULL,              'PRODUCT',            'NONE',    730, TRUE),
    ('INVOICE_ISSUE',  'Invoice generation',          'Invoice creation and issuance events.',                          'BILLING',        NULL,              'INVOICE',            'NONE',   1825, TRUE),
    ('INVOICE_EXPORT', 'Invoice export',              'PDF downloads and invoice exports.',                             'BILLING',        NULL,              'INVOICE_EXPORT',     'NONE',    730, TRUE),
    ('DATA_EXPORT',    'Data export',                 'Excel exports of products, invoices or audit records.',          'BILLING',        NULL,              'EXPORT',             'NONE',    730, TRUE),
    ('SUBSCRIPTION',   'Subscription changes',        'Plan assignment, renewal, cancellation and expiry notices.',     'SUBSCRIPTION',   NULL,              'SUBSCRIPTION',       'NONE',   1825, TRUE),
    ('TEMPLATE_CHANGE','Template changes',            'Changes to bill templates and custom templates.',                'TEMPLATE',       NULL,              'TEMPLATE',           'NONE',    730, TRUE),
    ('ADMIN_ACTION',   'Administrative actions',      'Privileged changes that are not covered elsewhere.',              'ADMINISTRATION', NULL,              NULL,                 'NONE',   1825, TRUE);

-- Audit types permitted per plan (an entitlement, like templates).
CREATE TABLE plan_audit_types (
    plan_id BIGINT NOT NULL REFERENCES subscription_plans(id) ON DELETE CASCADE,
    audit_type_id BIGINT NOT NULL REFERENCES audit_types(id) ON DELETE CASCADE,
    PRIMARY KEY (plan_id, audit_type_id)
);
CREATE INDEX idx_plan_audit_types_type ON plan_audit_types(audit_type_id);

-- Each audit history row points at the definition that governs its quota and retention.
ALTER TABLE audit_records ADD COLUMN audit_type_id BIGINT REFERENCES audit_types(id) ON DELETE SET NULL;
CREATE INDEX idx_audit_records_type_time ON audit_records(audit_type_id, created_at DESC);

-- --------------------------------------------------------------------------------------
-- 2. Versioned, soft-deletable invoice templates
-- --------------------------------------------------------------------------------------
ALTER TABLE bill_templates ADD COLUMN header_text VARCHAR(500);
ALTER TABLE bill_templates ADD COLUMN footer_text VARCHAR(500);
ALTER TABLE bill_templates ADD COLUMN payment_terms VARCHAR(500);
ALTER TABLE bill_templates ADD COLUMN terms_and_conditions VARCHAR(2000);
ALTER TABLE bill_templates ADD COLUMN version INTEGER NOT NULL DEFAULT 1 CHECK (version >= 1);
ALTER TABLE bill_templates ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE bill_templates ADD COLUMN deleted_by VARCHAR(190);
ALTER TABLE bill_templates ADD COLUMN updated_by VARCHAR(190);
ALTER TABLE bill_templates DROP CONSTRAINT bill_templates_code_key;
CREATE UNIQUE INDEX uq_bill_templates_code ON bill_templates(code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_bill_templates_name ON bill_templates(name) WHERE deleted_at IS NULL;

-- A generated invoice keeps the template version it was produced with, so editing a template
-- never silently rewrites history.
ALTER TABLE invoices ADD COLUMN template_version_snapshot INTEGER;
UPDATE invoices SET template_version_snapshot = 1 WHERE template_id IS NOT NULL;

ALTER TABLE custom_invoice_templates ADD COLUMN version INTEGER NOT NULL DEFAULT 1 CHECK (version >= 1);
ALTER TABLE custom_invoice_templates ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
CREATE UNIQUE INDEX uq_custom_templates_name_live ON custom_invoice_templates(customer_id, name) WHERE deleted_at IS NULL;
ALTER TABLE custom_invoice_templates DROP CONSTRAINT uq_custom_template_customer_name;

-- --------------------------------------------------------------------------------------
-- 3. Dynamic notifications and their delivery history
-- --------------------------------------------------------------------------------------
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    title VARCHAR(190) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'IN_APP' CHECK (channel IN ('EMAIL', 'IN_APP', 'BOTH')),
    trigger_event VARCHAR(48) NOT NULL,
    recipient_type VARCHAR(16) NOT NULL DEFAULT 'CUSTOMERS' CHECK (recipient_type IN ('CUSTOMERS', 'ADMINS')),
    plan_filter VARCHAR(400),
    status_filter VARCHAR(24) NOT NULL DEFAULT 'ACTIVE_ONLY'
        CHECK (status_filter IN ('ALL', 'ACTIVE_ONLY', 'DISABLED_ONLY', 'WITH_SUBSCRIPTION', 'WITHOUT_SUBSCRIPTION')),
    schedule_expression VARCHAR(64),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    last_run_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(190),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_notifications_name UNIQUE (name),
    CONSTRAINT ck_notifications_trigger CHECK (trigger_event ~ '^[A-Z][A-Z0-9_]{2,47}$'),
    CONSTRAINT ck_notifications_schedule CHECK (schedule_expression IS NULL OR length(btrim(schedule_expression)) > 0)
);
CREATE INDEX idx_notifications_trigger ON notifications(trigger_event, active);

CREATE TABLE notification_deliveries (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT REFERENCES notifications(id) ON DELETE SET NULL,
    notification_name VARCHAR(120) NOT NULL,
    trigger_event VARCHAR(48) NOT NULL,
    channel VARCHAR(16) NOT NULL CHECK (channel IN ('EMAIL', 'IN_APP')),
    customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    recipient VARCHAR(190) NOT NULL,
    title VARCHAR(190) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SENT', 'SIMULATED', 'FAILED', 'SKIPPED')),
    failure_reason VARCHAR(500),
    job_id BIGINT,
    idempotency_key VARCHAR(255) NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_delivery_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_notification_deliveries_customer ON notification_deliveries(customer_id, created_at DESC);
CREATE INDEX idx_notification_deliveries_status ON notification_deliveries(status, created_at DESC);
CREATE INDEX idx_notification_deliveries_job ON notification_deliveries(job_id) WHERE job_id IS NOT NULL;
