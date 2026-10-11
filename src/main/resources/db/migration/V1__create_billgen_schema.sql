CREATE SEQUENCE invoice_number_seq START WITH 1001 INCREMENT BY 1;

CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    company_name VARCHAR(160) NOT NULL,
    contact_name VARCHAR(120) NOT NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    phone VARCHAR(40),
    billing_address VARCHAR(255),
    city VARCHAR(80),
    state VARCHAR(80),
    postal_code VARCHAR(24),
    country VARCHAR(80) NOT NULL DEFAULT 'India',
    gstin VARCHAR(24),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(24) NOT NULL CHECK (role IN ('ADMIN', 'FINANCE', 'CUSTOMER')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    customer_id BIGINT UNIQUE REFERENCES customers(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(32) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'SENT', 'PAID')),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    template_code VARCHAR(20) NOT NULL DEFAULT 'CLASSIC' CHECK (template_code IN ('CLASSIC', 'MODERN', 'MINIMAL')),
    notes VARCHAR(1200),
    subtotal NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_invoice_dates CHECK (due_date >= issue_date)
);
CREATE INDEX idx_invoices_customer_date ON invoices(customer_id, issue_date DESC);
CREATE INDEX idx_invoices_status_due ON invoices(status, due_date);

CREATE TABLE invoice_items (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description VARCHAR(240) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(14,2) NOT NULL CHECK (unit_price >= 0),
    tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (tax_rate >= 0 AND tax_rate <= 100),
    line_subtotal NUMERIC(14,2) NOT NULL,
    tax_amount NUMERIC(14,2) NOT NULL,
    line_total NUMERIC(14,2) NOT NULL
);
CREATE INDEX idx_invoice_items_invoice ON invoice_items(invoice_id);

CREATE TABLE catalog_products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    product_type VARCHAR(16) NOT NULL CHECK (product_type IN ('PRODUCT', 'SERVICE')),
    category VARCHAR(80),
    description VARCHAR(500),
    unit_price NUMERIC(14,2) NOT NULL CHECK (unit_price >= 0),
    tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (tax_rate >= 0 AND tax_rate <= 100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_catalog_products_active_name ON catalog_products(active, name);

CREATE TABLE subscription_plans (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    tagline VARCHAR(160),
    description VARCHAR(500),
    monthly_price NUMERIC(14,2) NOT NULL CHECK (monthly_price >= 0),
    annual_price NUMERIC(14,2) NOT NULL CHECK (annual_price >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    max_customers INTEGER NOT NULL CHECK (max_customers > 0),
    max_monthly_invoices INTEGER NOT NULL CHECK (max_monthly_invoices > 0),
    features TEXT NOT NULL,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_subscription_plans_active_sort ON subscription_plans(active, sort_order, name);
