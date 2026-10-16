-- ============================================================================ Notification settings, pruned
-- Why this file exists at all: V12 was edited after it had been merged, and Flyway stores a checksum for
-- every applied version. On any database that had already run V12, a changed file behind an applied version
-- makes validation refuse the migration - and the report is nothing like its cause, because Spring then
-- fails to build the shared EntityManager and the first error in the log is an unsatisfied dependency on
-- 'jpaSharedEM_entityManagerFactory'. Migrations are therefore append-only: V12 is exactly what it was
-- when it merged, and the correction happens here.
--
-- What it corrects: notification_settings is the switch panel for the notices the application raises, so
-- a row nothing reads is a switch that does nothing. The platform has producers for six codes today
-- (the expiry ladder, the expiry notice, the two invoice-allowance notices, a recorded payment and a
-- credential change), so the rows for events this build cannot raise are removed rather than left on the
-- screen. When such an event exists, its code arrives with the producer, in a migration, not as a free-text
-- row somebody can type into an editor.
--
-- Both histories end in the same place: a database that has never seen these rows runs the inserts below
-- and ends with six; a database that ran the old V12 deletes six and inserts one. Deleting a code that is
-- not there is a no-op, and every statement is guarded, so the file can be replayed on either path.

DELETE FROM notification_settings
 WHERE notification_code IN ('PAYMENT_FAILED', 'WELCOME_EMAIL', 'PLAN_CHANGED',
                             'ACCOUNT_STATUS_CHANGE', 'AUDIT_LIMIT_WARNING', 'JOB_FAILED');

-- The notice an account must never be able to hide from itself. Its row exists so the EMAIL half can be
-- configured; the in-app entry is written by the security flow whatever this row says, which the
-- description states instead of the interface having to remember.
INSERT INTO notification_settings (notification_code, notification_name, description, email_enabled,
        in_app_enabled, trigger_days_before, repeat_interval_days, status, created_by, updated_by)
SELECT 'CREDENTIAL_CHANGED', 'Password or credentials changed',
       'In-app delivery of this notice is forced - an account must not be able to hide a credential change '
       'from itself - and this row decides only whether an email is sent as well.',
       TRUE, TRUE, 0, 0, 'ACTIVE', 'migration:v13', 'migration:v13'
 WHERE NOT EXISTS (SELECT 1 FROM notification_settings WHERE notification_code = 'CREDENTIAL_CHANGED');

-- The two numbers on the expiry reminder are now arithmetic rather than decoration, so the row says how
-- they are read: a lead time, an interval, and always a notice on the last day.
UPDATE notification_settings
   SET description = 'Reminds the account before its term ends. Reminders start this many days early and '
                     || 'repeat at the interval until one day remains, so seven days'' notice with a four '
                     || 'day interval warns at seven, three and one day.',
       updated_at = CURRENT_TIMESTAMP,
       updated_by = 'migration:v13'
 WHERE notification_code = 'SUBSCRIPTION_EXPIRY_WARNING';

-- Six codes remain, each one raised by a job or a flow, and none of them promises a delivery the
-- application cannot make.
COMMENT ON TABLE notification_settings IS
    'What the platform announces: one row per event a producer exists for. Codes are owned by the '
    'application (jobs look rows up by code), so only the configuration is editable.';
