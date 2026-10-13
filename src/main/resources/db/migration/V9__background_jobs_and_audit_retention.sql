-- BillGen V9 — Super Admin control plane, part 3: durable background jobs and audit retention.
--   * jobs are executed by batch records claimed with FOR UPDATE SKIP LOCKED, so a bulk email is
--     never sent from inside a web request and two workers can never take the same batch
--   * recipients are recorded one row at a time with a per-job unique key: a retry can never
--     send the same customer a second copy of an already-delivered message
--   * the append-only audit trigger is replaced so that a retention purge is possible while
--     UPDATE remains forbidden and DELETE only succeeds inside a session that declares a cutoff

-- --------------------------------------------------------------------------------------
-- 1. Jobs and batches
-- --------------------------------------------------------------------------------------
CREATE TABLE background_jobs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    job_type VARCHAR(40) NOT NULL CHECK (job_type IN
        ('SUBSCRIPTION_REMINDER', 'EXPIRY_NOTIFICATION', 'PLAN_CHANGE_NOTIFICATION',
         'USAGE_LIMIT_ALERT', 'CUSTOMER_EMAIL', 'SCHEDULED_REPORT', 'AUDIT_RETENTION_PURGE')),
    description VARCHAR(500),
    trigger_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL' CHECK (trigger_type IN ('MANUAL', 'SCHEDULED')),
    run_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recurrence VARCHAR(16) NOT NULL DEFAULT 'NONE' CHECK (recurrence IN ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY')),
    target_filter VARCHAR(400),
    notification_id BIGINT REFERENCES notifications(id) ON DELETE SET NULL,
    batch_size INTEGER NOT NULL DEFAULT 100 CHECK (batch_size BETWEEN 1 AND 500),
    retry_limit INTEGER NOT NULL DEFAULT 2 CHECK (retry_limit BETWEEN 0 AND 10),
    retry_delay_minutes INTEGER NOT NULL DEFAULT 15 CHECK (retry_delay_minutes BETWEEN 0 AND 1440),
    priority INTEGER NOT NULL DEFAULT 5 CHECK (priority BETWEEN 1 AND 9),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK (status IN
        ('PENDING', 'SCHEDULED', 'RUNNING', 'COMPLETED', 'PARTIALLY_FAILED', 'FAILED', 'CANCELLED', 'PAUSED')),
    eligible_count INTEGER NOT NULL DEFAULT 0 CHECK (eligible_count >= 0),
    batch_count INTEGER NOT NULL DEFAULT 0 CHECK (batch_count >= 0),
    processed_count INTEGER NOT NULL DEFAULT 0 CHECK (processed_count >= 0),
    success_count INTEGER NOT NULL DEFAULT 0 CHECK (success_count >= 0),
    failed_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_count >= 0),
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(190),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_background_jobs_claim ON background_jobs(status, priority DESC, run_at);
CREATE INDEX idx_background_jobs_created ON background_jobs(created_at DESC);

CREATE TABLE job_batches (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES background_jobs(id) ON DELETE CASCADE,
    batch_number INTEGER NOT NULL CHECK (batch_number >= 1),
    offset_index INTEGER NOT NULL CHECK (offset_index >= 0),
    item_count INTEGER NOT NULL CHECK (item_count >= 0),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'PAUSED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    success_count INTEGER NOT NULL DEFAULT 0 CHECK (success_count >= 0),
    failed_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_count >= 0),
    last_error VARCHAR(500),
    claimed_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_job_batches_number UNIQUE (job_id, batch_number)
);
CREATE INDEX idx_job_batches_claim ON job_batches(status, id);

CREATE TABLE job_recipients (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES job_batches(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES background_jobs(id) ON DELETE CASCADE,
    customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    recipient VARCHAR(190) NOT NULL,
    subject VARCHAR(190) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SENT', 'SIMULATED', 'FAILED', 'SKIPPED', 'RETRY_SCHEDULED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    idempotency_key VARCHAR(255) NOT NULL,
    last_error VARCHAR(500),
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- One row per recipient per job: retries reuse the row instead of queueing a second message.
    CONSTRAINT uq_job_recipients_unique UNIQUE (job_id, idempotency_key)
);
CREATE INDEX idx_job_recipients_batch ON job_recipients(batch_id, status);

-- --------------------------------------------------------------------------------------
-- 2. Controlled audit retention
-- --------------------------------------------------------------------------------------
-- V3 installed a trigger that refused every UPDATE and DELETE. Retention now needs a purge, so
-- the trigger is replaced with a narrower guard:
--   * UPDATE of an audit row is always refused (history is append-only);
--   * DELETE is only permitted inside a session that has set app.audit_purge_cutoff to an ISO
--     instant, and even then only for rows created at or before that instant.
CREATE FUNCTION deny_audit_mutation() RETURNS trigger AS $$
DECLARE
    cutoff text := current_setting('app.audit_purge_cutoff', TRUE);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'audit records are append-only and cannot be updated';
    END IF;
    IF cutoff IS NULL OR btrim(cutoff) = '' THEN
        RAISE EXCEPTION 'audit records may only be deleted by a retention purge session';
    END IF;
    IF NOT (btrim(cutoff) ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T') THEN
        RAISE EXCEPTION 'audit purge cutoff must be an ISO-8601 instant';
    END IF;
    IF OLD.created_at > btrim(cutoff)::timestamp with time zone THEN
        RAISE EXCEPTION 'audit purge may only remove records older than the declared cutoff';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_records_append_only ON audit_records;
CREATE TRIGGER trg_audit_records_retention_guard
    BEFORE UPDATE OR DELETE ON audit_records
    FOR EACH ROW EXECUTE FUNCTION deny_audit_mutation();
