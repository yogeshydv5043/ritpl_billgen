CREATE TABLE custom_invoice_templates (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    style_key VARCHAR(20) NOT NULL CHECK (style_key IN ('CLASSIC', 'MODERN', 'MINIMAL')),
    accent_color VARCHAR(7),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_custom_template_customer_name UNIQUE (customer_id, name),
    CONSTRAINT ck_custom_template_accent CHECK (accent_color IS NULL OR accent_color ~ '^#[0-9A-Fa-f]{6}$')
);
CREATE INDEX idx_custom_templates_customer_active ON custom_invoice_templates(customer_id, active, name);

ALTER TABLE invoices ALTER COLUMN template_id DROP NOT NULL;
ALTER TABLE invoices ADD COLUMN custom_template_id BIGINT REFERENCES custom_invoice_templates(id) ON DELETE SET NULL;
CREATE INDEX idx_invoices_custom_template ON invoices(custom_template_id);
