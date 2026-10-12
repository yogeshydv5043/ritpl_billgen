-- Snapshot the timestamp used for the Asia/Kolkata calendar-month invoice report.
ALTER TABLE invoices ADD COLUMN generated_at TIMESTAMP WITH TIME ZONE;
UPDATE invoices SET generated_at = created_at AT TIME ZONE 'UTC';
ALTER TABLE invoices ALTER COLUMN generated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE invoices ALTER COLUMN generated_at SET NOT NULL;

-- A sent/issued invoice consumes a slot for the subscription term it was issued under.
-- Drafts do not consume allowance; cancellation never clears this flag.
ALTER TABLE invoices ADD COLUMN usage_counted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE invoices ADD COLUMN usage_subscription_id BIGINT REFERENCES customer_subscriptions(id) ON DELETE SET NULL;
UPDATE invoices SET usage_counted = TRUE WHERE status IN ('SENT', 'PAID', 'CANCELLED');
CREATE INDEX idx_invoices_generated_at ON invoices(generated_at);
CREATE INDEX idx_invoices_usage_subscription ON invoices(usage_subscription_id) WHERE usage_counted = TRUE;

-- Carry historic SENT/PAID usage into each currently active term once. These older invoices
-- intentionally have no usage_subscription_id; new issues are counted by subscription id.
UPDATE customer_subscriptions s
SET invoice_usage_carry_forward = (
    SELECT COUNT(*)::INTEGER FROM invoices i
    WHERE i.customer_id = s.customer_id AND i.status IN ('SENT', 'PAID', 'CANCELLED')
)
WHERE s.status = 'ACTIVE';

ALTER TABLE invoices ADD CONSTRAINT ck_invoice_discount_within_gross
    CHECK (discount_amount <= subtotal + tax_amount);
