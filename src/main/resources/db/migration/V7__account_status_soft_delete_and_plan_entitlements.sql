-- BillGen V7 — Super Admin control plane, part 1: accounts and plan entitlements.
--   * customer accounts gain an explicit status and soft deletion (history is never destroyed)
--   * plans gain the configuration a Super Admin needs: billing cycle, grace period, renewal
--     rules, over-limit behaviour, usage warnings, expiry reminders and notification offsets

-- --------------------------------------------------------------------------------------
-- 1. Customer accounts
-- --------------------------------------------------------------------------------------
ALTER TABLE customers ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'DISABLED'));
ALTER TABLE customers ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE customers ADD COLUMN deleted_by VARCHAR(190);
ALTER TABLE customers ADD COLUMN deletion_reason VARCHAR(400);
-- The account email stays reserved while an account is soft deleted so that a new registration
-- cannot quietly inherit another company's invoices or audit history.
ALTER TABLE customers DROP CONSTRAINT customers_email_key;
CREATE UNIQUE INDEX uq_customers_email_live ON customers (email) WHERE deleted_at IS NULL;

UPDATE app_users u SET enabled = FALSE
FROM customers c WHERE c.id = u.customer_id AND c.status = 'DISABLED';

CREATE INDEX idx_customers_status_created ON customers(status, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_customers_deleted ON customers(deleted_at DESC) WHERE deleted_at IS NOT NULL;

-- --------------------------------------------------------------------------------------
-- 2. Plan configuration
-- --------------------------------------------------------------------------------------
ALTER TABLE subscription_plans ADD COLUMN billing_cycle VARCHAR(16) NOT NULL DEFAULT 'MONTHLY'
    CHECK (billing_cycle IN ('MONTHLY', 'YEARLY', 'CUSTOM'));
ALTER TABLE subscription_plans ADD COLUMN billing_period_days INTEGER;
ALTER TABLE subscription_plans ADD COLUMN grace_period_days INTEGER NOT NULL DEFAULT 0
    CHECK (grace_period_days >= 0 AND grace_period_days <= 180);
ALTER TABLE subscription_plans ADD COLUMN over_limit_behavior VARCHAR(16) NOT NULL DEFAULT 'BLOCK'
    CHECK (over_limit_behavior IN ('BLOCK', 'WARN'));
ALTER TABLE subscription_plans ADD COLUMN usage_warning_percent INTEGER NOT NULL DEFAULT 80
    CHECK (usage_warning_percent BETWEEN 1 AND 100);
ALTER TABLE subscription_plans ADD COLUMN notify_at_warning BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE subscription_plans ADD COLUMN notify_at_limit BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE subscription_plans ADD COLUMN audit_daily_limit INTEGER;
ALTER TABLE subscription_plans ADD COLUMN audit_monthly_limit INTEGER;
ALTER TABLE subscription_plans ADD COLUMN audit_yearly_limit INTEGER;
ALTER TABLE subscription_plans ADD COLUMN audit_default_retention_days INTEGER NOT NULL DEFAULT 730
    CHECK (audit_default_retention_days >= 1);
ALTER TABLE subscription_plans ADD COLUMN auto_renewal_allowed BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE subscription_plans ADD COLUMN allow_downgrade BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE subscription_plans ADD COLUMN allow_midterm_upgrade BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE subscription_plans ADD COLUMN expiry_reminder_days VARCHAR(64) NOT NULL DEFAULT '30,7,3,1'
    CHECK (expiry_reminder_days ~ '^[0-9]+(,[0-9]+)*$');
ALTER TABLE subscription_plans ADD COLUMN custom_period_days INTEGER;
ALTER TABLE subscription_plans ADD COLUMN plan_start_on DATE;
ALTER TABLE subscription_plans ADD COLUMN plan_end_on DATE;
ALTER TABLE subscription_plans ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE subscription_plans ADD COLUMN deleted_by VARCHAR(190);
ALTER TABLE subscription_plans ADD COLUMN updated_by VARCHAR(190);
ALTER TABLE subscription_plans ADD CONSTRAINT ck_subscription_plans_custom_period
    CHECK (billing_cycle <> 'CUSTOM' OR (billing_period_days IS NOT NULL AND billing_period_days BETWEEN 1 AND 3650));
ALTER TABLE subscription_plans ADD CONSTRAINT ck_subscription_plans_custom_period_days
    CHECK (custom_period_days IS NULL OR custom_period_days BETWEEN 1 AND 3650);
ALTER TABLE subscription_plans ADD CONSTRAINT ck_subscription_plans_period
    CHECK (plan_end_on IS NULL OR plan_start_on IS NULL OR plan_end_on >= plan_start_on);
CREATE UNIQUE INDEX uq_subscription_plans_name ON subscription_plans(name) WHERE deleted_at IS NULL;
