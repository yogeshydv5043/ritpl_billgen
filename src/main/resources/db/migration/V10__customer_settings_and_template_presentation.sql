-- Customer Panel: the working preferences each company account keeps for itself, the presentation
-- blocks a customer may customize on its own invoice templates, and the indexes the paged, filtered
-- customer lists need.
--
-- Nothing here changes an issued invoice: the presentation values a customer sets are copied onto a
-- new invoice as snapshots (the *_snapshot columns), so a template edit can never rewrite a document
-- that has already been delivered.

-- --------------------------------------------------------------------------- per-account settings
-- One row per customer, keyed by the account id so a missing row is simply "the account has never
-- saved its preferences" rather than a second join to reason about.
CREATE TABLE customer_settings (
    customer_id          bigint PRIMARY KEY,
    CONSTRAINT fk_customer_settings_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,

    -- Invoice defaults. A default template must be one the account is entitled to; the service
    -- re-checks that on every save because plans change underneath the row.
    default_template_id        bigint,
    default_custom_template_id bigint,
    default_currency           varchar(3)  NOT NULL DEFAULT 'INR',
    invoice_number_prefix      varchar(8)  NOT NULL DEFAULT 'INV-',
    default_due_days           integer     NOT NULL DEFAULT 14,
    default_tax_rate           numeric(5,2),
    default_payment_terms      varchar(240),
    default_notes              varchar(600),
    default_footer             varchar(600),

    -- Catalogue defaults and list preferences.
    default_unit        varchar(24),
    default_category_id bigint,
    list_page_size      integer NOT NULL DEFAULT 20,

    -- How far back the account may browse its own audit trail. This is a viewing window only: the
    -- retention rule that deletes history lives on the audit type and the retention job, and is
    -- deliberately not stored here so the two can never disagree silently.
    audit_history_period varchar(16) NOT NULL DEFAULT 'ONE_YEAR',

    -- Notification preferences. A disabled flag stops the platform generating that message for this
    -- account; it never cancels a subscription or blocks billing.
    notify_expiry_reminders boolean NOT NULL DEFAULT true,
    notify_quota_alerts     boolean NOT NULL DEFAULT true,
    notify_payment_updates  boolean NOT NULL DEFAULT true,
    notify_security_alerts  boolean NOT NULL DEFAULT true,

    -- Export preference: how wide a period an export link covers when the caller sets no dates.
    export_range_days integer NOT NULL DEFAULT 90,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_customer_settings_currency CHECK (default_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_customer_settings_prefix CHECK (invoice_number_prefix ~ '^[A-Z][A-Z0-9-]{0,7}$'),
    CONSTRAINT chk_customer_settings_due_days CHECK (default_due_days BETWEEN 0 AND 365),
    CONSTRAINT chk_customer_settings_tax_rate CHECK (default_tax_rate IS NULL OR (default_tax_rate >= 0 AND default_tax_rate <= 100)),
    CONSTRAINT chk_customer_settings_page CHECK (list_page_size IN (10, 20, 50, 100)),
    CONSTRAINT chk_customer_settings_audit CHECK (audit_history_period IN ('DAYS_15', 'ONE_MONTH', 'THREE_MONTHS', 'SIX_MONTHS', 'ONE_YEAR')),
    CONSTRAINT chk_customer_settings_export_range CHECK (export_range_days BETWEEN 7 AND 730)
);

ALTER TABLE customer_settings
    ADD CONSTRAINT fk_customer_settings_template FOREIGN KEY (default_template_id) REFERENCES bill_templates (id) ON DELETE SET NULL;
ALTER TABLE customer_settings
    ADD CONSTRAINT fk_customer_settings_custom_template FOREIGN KEY (default_custom_template_id) REFERENCES custom_invoice_templates (id) ON DELETE SET NULL;
ALTER TABLE customer_settings
    ADD CONSTRAINT fk_customer_settings_category FOREIGN KEY (default_category_id) REFERENCES product_categories (id) ON DELETE SET NULL;

-- --------------------------------------------------------------------- template customization
-- The parts of a layout a customer may change on its own copy. The system template row is never
-- touched: these columns exist only on custom_invoice_templates.
ALTER TABLE custom_invoice_templates
    ADD COLUMN font_key          varchar(16) NOT NULL DEFAULT 'DEFAULT',
    ADD COLUMN layout_density    varchar(16) NOT NULL DEFAULT 'COMFORTABLE',
    ADD COLUMN show_logo         boolean     NOT NULL DEFAULT true,
    ADD COLUMN show_bank_details boolean     NOT NULL DEFAULT false,
    ADD COLUMN bank_details      varchar(600),
    ADD COLUMN payment_terms     varchar(240),
    ADD COLUMN footer_text       varchar(600),
    ADD COLUMN signature_label   varchar(120),
    ADD COLUMN signature_name    varchar(120),
    ADD CONSTRAINT chk_custom_template_font CHECK (font_key IN ('DEFAULT', 'SERIF', 'MONO')),
    ADD CONSTRAINT chk_custom_template_density CHECK (layout_density IN ('COMFORTABLE', 'COMPACT'));

-- Snapshots of those choices, taken when an invoice is issued, so a later edit cannot alter a
-- document that was already delivered.
ALTER TABLE invoices
    ADD COLUMN template_font_snapshot        varchar(16),
    ADD COLUMN template_density_snapshot     varchar(16),
    ADD COLUMN template_terms_snapshot       varchar(240),
    ADD COLUMN template_footer_snapshot      varchar(600),
    ADD COLUMN template_bank_snapshot        varchar(600),
    ADD COLUMN template_show_logo_snapshot      boolean,
    ADD COLUMN template_signature_label_snapshot varchar(120),
    ADD COLUMN template_signature_name_snapshot  varchar(120);

-- --------------------------------------------------------------------- catalogue completeness
-- The unit a product is sold in (hour, piece, licence) and whether a category is still offered.
ALTER TABLE catalog_products
    ADD COLUMN unit varchar(24);
ALTER TABLE product_categories
    ADD COLUMN active boolean NOT NULL DEFAULT true;

-- --------------------------------------------------------------------------- list performance
-- The customer lists filter and sort inside one account, so every useful index leads with the tenant.
CREATE INDEX IF NOT EXISTS idx_invoices_customer_created ON invoices (customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_invoices_customer_status_issue ON invoices (customer_id, status, issue_date DESC);
CREATE INDEX IF NOT EXISTS idx_catalog_products_customer_name ON catalog_products (customer_id, name);
CREATE INDEX IF NOT EXISTS idx_catalog_products_customer_active ON catalog_products (customer_id, active);
CREATE INDEX IF NOT EXISTS idx_product_categories_customer_name ON product_categories (customer_id, name);

-- ------------------------------------------------------------------- legacy invoices, given their own copies
-- Before this migration an invoice read its layout from the live template row, so a listing page had to
-- dereference a lazily loaded association, and an administrator editing a template changed what an old
-- document appeared to have said. Copying the current values into the snapshot columns at upgrade time
-- ends both: from here the snapshot is always present, and it is the same text the invoice was printed
-- with on the day these columns arrived.
UPDATE invoices i
   SET template_name_snapshot   = COALESCE(i.template_name_snapshot, t.name),
       template_style_snapshot  = COALESCE(i.template_style_snapshot, t.style_key),
       template_accent_snapshot = COALESCE(i.template_accent_snapshot, t.accent_color)
  FROM bill_templates t
 WHERE i.template_id = t.id;

UPDATE invoices i
   SET template_name_snapshot          = COALESCE(i.template_name_snapshot, c.name),
       template_style_snapshot          = COALESCE(i.template_style_snapshot, c.style_key),
       template_accent_snapshot         = COALESCE(i.template_accent_snapshot, c.accent_color),
       template_font_snapshot           = COALESCE(i.template_font_snapshot, c.font_key),
       template_density_snapshot        = COALESCE(i.template_density_snapshot, c.layout_density),
       template_terms_snapshot          = COALESCE(i.template_terms_snapshot, c.payment_terms),
       template_footer_snapshot         = COALESCE(i.template_footer_snapshot, c.footer_text),
       template_bank_snapshot           = COALESCE(i.template_bank_snapshot, c.bank_details),
       template_show_logo_snapshot      = COALESCE(i.template_show_logo_snapshot, c.show_logo),
       template_signature_label_snapshot = COALESCE(i.template_signature_label_snapshot, c.signature_label),
       template_signature_name_snapshot = COALESCE(i.template_signature_name_snapshot, c.signature_name)
  FROM custom_invoice_templates c
 WHERE i.custom_template_id = c.id;
