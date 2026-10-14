-- The account inbox is no longer only about subscription expiry: a customer's own settings decide
-- whether quota, payment and security notices arrive, and a payment or security notice is not tied to a
-- subscription term at all. So the type list is widened, the term reference becomes optional, and the
-- lookup the inbox performs is indexed.
ALTER TABLE subscription_notification_logs
    DROP CONSTRAINT subscription_notification_logs_notification_type_check;
ALTER TABLE subscription_notification_logs
    ADD CONSTRAINT subscription_notification_logs_notification_type_check
    CHECK (notification_type IN ('EXPIRING', 'EXPIRED', 'QUOTA', 'PAYMENT', 'SECURITY'));
ALTER TABLE subscription_notification_logs
    ALTER COLUMN subscription_id DROP NOT NULL;
CREATE INDEX idx_subscription_notifications_inbox
    ON subscription_notification_logs(customer_id, notification_type, days_before);
