ALTER TABLE customers ADD COLUMN logo_data BYTEA;
ALTER TABLE customers ADD COLUMN logo_content_type VARCHAR(40);

ALTER TABLE invoices
    ADD COLUMN company_name_snapshot VARCHAR(160),
    ADD COLUMN company_contact_snapshot VARCHAR(120),
    ADD COLUMN company_email_snapshot VARCHAR(190),
    ADD COLUMN company_phone_snapshot VARCHAR(40),
    ADD COLUMN company_address_snapshot VARCHAR(600),
    ADD COLUMN company_gstin_snapshot VARCHAR(24),
    ADD COLUMN company_logo_snapshot BYTEA,
    ADD COLUMN company_logo_content_type_snapshot VARCHAR(40),
    ADD COLUMN template_name_snapshot VARCHAR(100),
    ADD COLUMN template_style_snapshot VARCHAR(20),
    ADD COLUMN template_accent_snapshot VARCHAR(7);

UPDATE invoices i SET
    company_name_snapshot = c.company_name,
    company_contact_snapshot = c.contact_name,
    company_email_snapshot = c.email,
    company_phone_snapshot = c.phone,
    company_address_snapshot = concat_ws(', ', NULLIF(c.billing_address, ''), NULLIF(c.city, ''),
        NULLIF(c.state, ''), NULLIF(c.postal_code, ''), NULLIF(c.country, '')),
    company_gstin_snapshot = c.gstin,
    template_name_snapshot = t.name,
    template_style_snapshot = t.style_key,
    template_accent_snapshot = t.accent_color
FROM customers c, bill_templates t
WHERE c.id = i.customer_id AND t.id = i.template_id;
