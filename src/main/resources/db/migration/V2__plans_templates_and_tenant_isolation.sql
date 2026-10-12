-- BillGen V2
--   * only two roles remain: ADMIN and CUSTOMER
--   * bill/invoice templates become admin-managed data instead of a hard-coded enum
--   * a plan carries the templates included with it
--   * a customer account holds the plan it purchased
--   * customer-owned rows (products, categories, invoices) are tenant scoped

-- --------------------------------------------------------------------------------------
-- 1. Roles: ADMIN and CUSTOMER only.
--    Existing FINANCE staff accounts are promoted to ADMIN so that no billing access
--    is silently lost when the role is removed.
-- --------------------------------------------------------------------------------------
DO $$
DECLARE constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = con.conkey[1]
        WHERE rel.relname = 'app_users'
          AND con.contype = 'c'
          AND att.attname = 'role'
    LOOP
        EXECUTE format('ALTER TABLE app_users DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

UPDATE app_users SET role = 'ADMIN' WHERE role = 'FINANCE';

ALTER TABLE app_users ADD CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'CUSTOMER'));

-- --------------------------------------------------------------------------------------
-- 2. Bill/invoice templates managed by the administrator.
--    style_key points at the print layout in app.css (template-classic|modern|minimal).
-- --------------------------------------------------------------------------------------
CREATE TABLE bill_templates (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    style_key VARCHAR(20) NOT NULL CHECK (style_key IN ('CLASSIC', 'MODERN', 'MINIMAL')),
    accent_color VARCHAR(7),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO bill_templates (code, name, description, style_key, accent_color, sort_order) VALUES
    ('CLASSIC', 'Classic', 'Balanced and professional.', 'CLASSIC', NULL, 10),
    ('MODERN', 'Modern', 'A bold contemporary accent.', 'MODERN', NULL, 20),
    ('MINIMAL', 'Minimal', 'A clean, compact statement.', 'MINIMAL', NULL, 30);

-- --------------------------------------------------------------------------------------
-- 3. The templates included with each plan (a plan may carry any number of templates).
-- --------------------------------------------------------------------------------------
CREATE TABLE plan_templates (
    plan_id BIGINT NOT NULL REFERENCES subscription_plans(id) ON DELETE CASCADE,
    template_id BIGINT NOT NULL REFERENCES bill_templates(id) ON DELETE CASCADE,
    PRIMARY KEY (plan_id, template_id)
);
CREATE INDEX idx_plan_templates_template ON plan_templates(template_id);

-- Existing active plans had no explicit template configuration. Preserve the old starter
-- behaviour (all three original render layouts) until an administrator configures each plan.
INSERT INTO plan_templates (plan_id, template_id)
SELECT p.id, t.id
FROM subscription_plans p CROSS JOIN bill_templates t
WHERE p.active = TRUE
ON CONFLICT (plan_id, template_id) DO NOTHING;

-- --------------------------------------------------------------------------------------
-- 4. The plan a customer account purchased. At most one active subscription per account.
-- --------------------------------------------------------------------------------------
CREATE TABLE customer_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES subscription_plans(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    started_on DATE NOT NULL DEFAULT CURRENT_DATE,
    ends_on DATE,
    assigned_by VARCHAR(190),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_customer_subscriptions_period CHECK (ends_on IS NULL OR ends_on >= started_on)
);
CREATE UNIQUE INDEX uq_customer_subscriptions_active ON customer_subscriptions(customer_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_customer_subscriptions_customer ON customer_subscriptions(customer_id, created_at DESC);
CREATE INDEX idx_customer_subscriptions_plan ON customer_subscriptions(plan_id);

-- --------------------------------------------------------------------------------------
-- 5. Product categories, owned by exactly one customer account.
-- --------------------------------------------------------------------------------------
CREATE TABLE product_categories (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(300),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_product_categories_customer_name UNIQUE (customer_id, name),
    CONSTRAINT uq_product_categories_id_customer UNIQUE (id, customer_id)
);
CREATE INDEX idx_product_categories_customer ON product_categories(customer_id, name);

-- --------------------------------------------------------------------------------------
-- 6. Catalogue items become customer owned. A SKU is unique inside one account only.
--    The old app had a globally shared catalogue and no tenant owner. Move those rows to a
--    hidden internal account rather than silently giving one real customer another's products.
-- --------------------------------------------------------------------------------------
ALTER TABLE customers ADD COLUMN system_account BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO customers (company_name, contact_name, email, country, system_account)
SELECT 'Legacy catalogue', 'BillGen migration', 'legacy-catalogue@billgen.local', 'India', TRUE
WHERE EXISTS (SELECT 1 FROM catalog_products)
  AND NOT EXISTS (SELECT 1 FROM customers WHERE system_account = TRUE)
ON CONFLICT (email) DO UPDATE SET system_account = TRUE;

ALTER TABLE catalog_products DROP CONSTRAINT IF EXISTS catalog_products_sku_key;
ALTER TABLE catalog_products ADD COLUMN customer_id BIGINT;
ALTER TABLE catalog_products ADD COLUMN category_id BIGINT;

UPDATE catalog_products
SET customer_id = (SELECT id FROM customers WHERE system_account = TRUE ORDER BY id LIMIT 1)
WHERE EXISTS (SELECT 1 FROM customers WHERE system_account = TRUE);

-- Preserve category names from the old free-text catalogue as account-owned category records.
INSERT INTO product_categories (customer_id, name)
SELECT DISTINCT p.customer_id, btrim(p.category)
FROM catalog_products p
WHERE p.category IS NOT NULL AND btrim(p.category) <> ''
ON CONFLICT (customer_id, name) DO NOTHING;

UPDATE catalog_products p
SET category_id = c.id
FROM product_categories c
WHERE p.customer_id = c.customer_id
  AND p.category IS NOT NULL
  AND btrim(p.category) = c.name;

ALTER TABLE catalog_products DROP COLUMN category;
ALTER TABLE catalog_products ALTER COLUMN customer_id SET NOT NULL;
ALTER TABLE catalog_products ADD CONSTRAINT fk_catalog_products_customer
    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE;
ALTER TABLE catalog_products ADD CONSTRAINT fk_catalog_products_category_tenant
    FOREIGN KEY (category_id, customer_id) REFERENCES product_categories(id, customer_id) ON DELETE RESTRICT;
ALTER TABLE catalog_products ADD CONSTRAINT uq_catalog_products_customer_sku UNIQUE (customer_id, sku);
CREATE INDEX idx_catalog_products_customer_name ON catalog_products(customer_id, name);

-- --------------------------------------------------------------------------------------
-- 7. Invoices reference a managed template instead of a hard-coded design code.
-- --------------------------------------------------------------------------------------
ALTER TABLE invoices ADD COLUMN template_id BIGINT REFERENCES bill_templates(id) ON DELETE RESTRICT;

UPDATE invoices i
SET template_id = t.id
FROM bill_templates t
WHERE t.code = i.template_code;

UPDATE invoices i
SET template_id = (SELECT t.id FROM bill_templates t WHERE t.code = 'CLASSIC')
WHERE i.template_id IS NULL;

ALTER TABLE invoices ALTER COLUMN template_id SET NOT NULL;
ALTER TABLE invoices DROP COLUMN template_code;
CREATE INDEX idx_invoices_template ON invoices(template_id);
ALTER TABLE invoices ADD CONSTRAINT uq_invoices_id_customer UNIQUE (id, customer_id);

-- Child invoice-line rows also carry the tenant id explicitly. The composite parent foreign key
-- prevents a line from being attached to an invoice owned by a different account.
ALTER TABLE invoice_items ADD COLUMN customer_id BIGINT;
UPDATE invoice_items item
SET customer_id = invoice.customer_id
FROM invoices invoice
WHERE invoice.id = item.invoice_id;
ALTER TABLE invoice_items ALTER COLUMN customer_id SET NOT NULL;
ALTER TABLE invoice_items ADD CONSTRAINT fk_invoice_items_customer
    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE;
ALTER TABLE invoice_items ADD CONSTRAINT fk_invoice_items_invoice_customer
    FOREIGN KEY (invoice_id, customer_id) REFERENCES invoices(id, customer_id) ON DELETE CASCADE;
CREATE INDEX idx_invoice_items_customer ON invoice_items(customer_id);