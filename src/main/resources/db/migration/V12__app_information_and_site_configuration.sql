-- ============================================================================ App Information module
-- Seven configuration tables and no more: application branding, the website's policy pages, reviews,
-- FAQ types, FAQs, email transport and notification settings. Social links and footer text live in
-- app_info rather than in a table of their own, and there is deliberately no site_settings row to keep
-- in step with these - one home for each fact.
--
-- app_info and email_settings describe one global thing each, so both are single-row tables: the
-- CHECK is what stops a second "current" configuration from appearing, which a service call alone
-- cannot guarantee if two administrators save at the same moment.

CREATE TABLE app_info (
    id BIGINT NOT NULL PRIMARY KEY,
    site_name VARCHAR(150) NOT NULL,
    site_tagline VARCHAR(255),
    site_logo_url VARCHAR(500),
    site_favicon_url VARCHAR(500),
    support_email VARCHAR(190),
    support_phone VARCHAR(40),
    support_whatsapp VARCHAR(40),
    primary_color VARCHAR(7) NOT NULL DEFAULT '#7357f4',
    secondary_color VARCHAR(7) NOT NULL DEFAULT '#19182f',
    instagram_url VARCHAR(500),
    facebook_url VARCHAR(500),
    youtube_url VARCHAR(500),
    x_url VARCHAR(500),
    footer_description VARCHAR(1000),
    copyright_text VARCHAR(500),
    disclaimer_text VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(190),
    updated_by VARCHAR(190),
    CONSTRAINT app_info_singleton CHECK (id = 1),
    CONSTRAINT app_info_primary_color_check CHECK (primary_color ~ '^#[0-9a-fA-F]{6}$'),
    CONSTRAINT app_info_secondary_color_check CHECK (secondary_color ~ '^#[0-9a-fA-F]{6}$')
);

-- One row per policy, so a type cannot be published twice. The unique key is the type itself:
-- the admin screen edits the existing row rather than adding a version nobody asked for.
CREATE TABLE policy_pages (
    id BIGSERIAL NOT NULL PRIMARY KEY,
    policy_type VARCHAR(24) NOT NULL UNIQUE CHECK (policy_type IN ('REFUND', 'PRIVACY', 'TERMS_CONDITIONS')),
    heading VARCHAR(200) NOT NULL,
    meta_title VARCHAR(200),
    meta_description VARCHAR(500),
    page_content VARCHAR(20000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'INACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(190),
    updated_by VARCHAR(190)
);

CREATE TABLE reviews (
    id BIGSERIAL NOT NULL PRIMARY KEY,
    reviewer_name VARCHAR(150) NOT NULL,
    review_content VARCHAR(2000) NOT NULL,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    location VARCHAR(120),
    designation VARCHAR(120),
    profile_image_url VARCHAR(500),
    display_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'INACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(190),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(190),
    updated_by VARCHAR(190)
);
CREATE INDEX idx_reviews_public ON reviews (status, display_order, id);

CREATE TABLE faq_types (
    id BIGSERIAL NOT NULL PRIMARY KEY,
    type_name VARCHAR(150) NOT NULL,
    type_code VARCHAR(48) NOT NULL UNIQUE,
    description VARCHAR(500),
    display_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(190),
    updated_by VARCHAR(190)
);

CREATE TABLE faqs (
    id BIGSERIAL NOT NULL PRIMARY KEY,
    faq_type_id BIGINT NOT NULL REFERENCES faq_types(id) ON DELETE RESTRICT,
    question VARCHAR(500) NOT NULL,
    answer VARCHAR(4000) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(190),
    updated_by VARCHAR(190)
);
-- Every public FAQ list and the admin filter "by type, by status" walks this.
CREATE INDEX idx_faqs_by_type ON faqs (faq_type_id, status, display_order, id);

-- --------------------------------------------------------------------------- email transport
-- The password is stored as an encrypted envelope (see SecretCipher); nothing here may return it,
-- and an empty submission means "leave it alone" rather than "clear it", so the columns are nullable
-- and the service never writes a null over a value it was not given.
CREATE TABLE email_settings (
    id BIGINT NOT NULL PRIMARY KEY,
    smtp_host VARCHAR(255),
    smtp_port INT CHECK (smtp_port IS NULL OR (smtp_port BETWEEN 1 AND 65535)),
    smtp_username VARCHAR(190),
    smtp_secret VARCHAR(1000),
    smtp_secret_set_at TIMESTAMP,
    auth_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    security_protocol VARCHAR(16) NOT NULL DEFAULT 'NONE'
        CHECK (security_protocol IN ('NONE', 'START_TLS', 'SSL_TLS')),
    timeout_ms INT NOT NULL DEFAULT 5000 CHECK (timeout_ms BETWEEN 250 AND 120000),
    sender_name VARCHAR(150),
    sender_email VARCHAR(190),
    reply_to_email VARCHAR(190),
    status VARCHAR(16) NOT NULL DEFAULT 'INACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(190),
    updated_by VARCHAR(190),
    CONSTRAINT email_settings_singleton CHECK (id = 1)
);

-- ------------------------------------------------------------------- notification settings
-- What the platform sends, to whom it is announced and how early - one row per event the application
-- can actually produce. The codes are seeded, not typed, because a code decides which job looks for
-- the row; only their configuration is editable. Rows are inserted only when nothing equivalent is
-- configured yet, so the migration is safe to run on a database that already has rules.
CREATE TABLE notification_settings (
    id BIGSERIAL NOT NULL PRIMARY KEY,
    notification_code VARCHAR(48) NOT NULL UNIQUE,
    notification_name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    trigger_days_before INT NOT NULL DEFAULT 0 CHECK (trigger_days_before BETWEEN 0 AND 365),
    repeat_interval_days INT NOT NULL DEFAULT 0 CHECK (repeat_interval_days BETWEEN 0 AND 365),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(190),
    updated_by VARCHAR(190)
);

INSERT INTO notification_settings (notification_code, notification_name, description, email_enabled,
        in_app_enabled, trigger_days_before, repeat_interval_days, status, created_by, updated_by)
SELECT seed.code, seed.name, seed.description, seed.email, seed.in_app, seed.days, seed.repeat, seed.status,
       'migration:v12', 'migration:v12'
  FROM (VALUES
        ('SUBSCRIPTION_EXPIRY_WARNING', 'Subscription expiry warning',
         'Reminds the account before its term ends. Reminders start this many days early and repeat at the '
         'interval until one day remains, so a seven day notice with a four day interval warns at seven, '
         'three and one day.', TRUE, TRUE, 7, 4, 'ACTIVE'),
        ('SUBSCRIPTION_EXPIRED', 'Subscription expired',
         'Records that a term has ended and the account has lost template access.', TRUE, TRUE, 0, 0, 'ACTIVE'),
        ('INVOICE_LIMIT_WARNING', 'Invoice allowance approaching its limit',
         'Raised when the term allowance reaches the plan''s warning threshold.', FALSE, TRUE, 0, 0, 'ACTIVE'),
        ('INVOICE_LIMIT_REACHED', 'Invoice allowance exhausted',
         'Raised when no further invoice can be issued in this period.', FALSE, TRUE, 0, 0, 'ACTIVE'),
        ('PAYMENT_SUCCESS', 'Payment recorded',
         'Raised for the account whose invoice was recorded as paid.', FALSE, TRUE, 0, 0, 'ACTIVE'),
        ('CREDENTIAL_CHANGED', 'Password or credentials changed',
         'In-app delivery of this notice is forced - an account must not be able to hide a credential change '
         'from itself - and this row decides only whether an email is sent as well.', TRUE, TRUE, 0, 0, 'ACTIVE')
       ) AS seed(code, name, description, email, in_app, days, repeat, status)
 WHERE NOT EXISTS (SELECT 1 FROM notification_settings);

-- Six codes, and every one of them is raised by the application today. Codes for events this build cannot
-- produce (a payment gateway failing, a welcome email, an audit-window notice) are deliberately absent:
-- a switch that nothing honours is worse than no switch, and the table is extended by migration when the
-- event exists rather than by an editor that would accept any code a person can type.

-- The one piece of branding the application needs before an administrator has opened the screen: the
-- name and the footer line the sign-in page already prints. Everything else starts empty, so a blank
-- field means "not configured" rather than a value somebody invented. Idempotent by construction -
-- the singleton CHECK allows exactly one row, and this one lands only if the table is empty.
INSERT INTO app_info (id, site_name, site_tagline, copyright_text, created_by, updated_by)
SELECT 1, 'BillGen', 'Invoice Studio', '© 2026 BillGen', 'migration:v12', 'migration:v12'
 WHERE NOT EXISTS (SELECT 1 FROM app_info);
