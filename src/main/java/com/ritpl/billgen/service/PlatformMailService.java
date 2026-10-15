package com.ritpl.billgen.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.DeliveryStatus;
import com.ritpl.billgen.entity.NotificationChannel;
import com.ritpl.billgen.entity.NotificationDelivery;
import com.ritpl.billgen.entity.NotificationSetting;
import com.ritpl.billgen.repository.NotificationDeliveryRepository;

/**
 * The one path a notice the platform produced itself takes on its way out by email: the setting decides
 * whether it is sent, the transport decides whether it went, and the delivery row records which of those
 * happened. Notices an administrator authored and queued as a job are rendered by the job pipeline and
 * use the same delivery table, so an administrator reading "deliveries" sees every message either way.
 *
 * <p>Three rules make this trustworthy rather than convenient. A delivery is recorded with the outcome the
 * transport actually reported, so a build without an SMTP server says {@code SIMULATED} instead of
 * pretending. The idempotency key is checked before anything is sent, so a retried job cannot email the
 * same person twice for the same event. And a notice that is switched off, or switched off for email,
 * writes nothing at all - not even a row saying it was skipped. The delivery row is the record of what
 * happened; the business event that caused the notice is audited by whoever raised it.</p>
 */
@Service
public class PlatformMailService {

    private final NotificationSettingService settings;
    private final NotificationDeliveryRepository deliveries;
    private final EmailSink sink;

    public PlatformMailService(NotificationSettingService settings, NotificationDeliveryRepository deliveries,
            EmailSink sink) {
        this.settings = settings; this.deliveries = deliveries; this.sink = sink;
    }

    /**
     * @param notificationCode the {@code notification_settings} code that governs this notice
     * @param idempotencyKey stable per event and recipient, so a retry repeats nothing
     */
    @Transactional
    public Outcome email(String notificationCode, Customer customer, String subject, String body,
            String idempotencyKey) {
        Optional<NotificationSetting> configured = settings.find(notificationCode);
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            return new Outcome(false, null, "the account has no email address on file");
        }
        if (configured.isEmpty()) {
            // No row means nothing was ever configured to send this - the notice stays in the inbox and
            // the reason is reported to the caller rather than swallowed.
            return new Outcome(false, null, "this notice is not configured, so no email was sent");
        }
        NotificationSetting setting = configured.get();
        if (!setting.isActive()) return new Outcome(false, null, "this notice is switched off");
        if (!setting.isEmailEnabled()) return new Outcome(false, null, "this notice is set to in-app only");
        if (deliveries.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return new Outcome(false, DeliveryStatus.SENT, "already sent for this event");
        }

        NotificationDelivery delivery = new NotificationDelivery(null, NotificationChannel.EMAIL, customer,
                customer.getEmail().trim(), clip(subject, 190), clip(body, 4000), idempotencyKey);
        delivery.describeSource(setting.getNotificationName(), setting.getNotificationCode());
        String detail;
        if (!sink.isLive()) {
            delivery.mark(DeliveryStatus.SIMULATED, null);
            detail = "recorded, not sent (" + sink.describe() + ")";
        } else {
            try {
                sink.send(delivery.getRecipient(), delivery.getTitle(), delivery.getBody());
                delivery.mark(DeliveryStatus.SENT, null);
                detail = "sent through " + sink.describe();
            } catch (Exception failed) {
                // The transport's own words are kept for the administrator, clipped to the column; the
                // recipient's notice is not lost, because the inbox row was written by the caller.
                delivery.mark(DeliveryStatus.FAILED, failed.getMessage());
                detail = "the mail server refused it (" + clip(shortName(failed), 160) + ")";
            }
        }
        // The delivery row is the evidence: it carries the recipient, the outcome and the failure detail,
        // so a notice does not also need an audit entry saying an email was written. Audit rows record the
        // business event that produced the notice, which is the caller's job.
        deliveries.save(delivery);
        return new Outcome(true, delivery.getStatus(), detail);
    }

    /** Whether the account's inbox should hold this notice at all. Absent means "keep the old behaviour". */
    @Transactional(readOnly = true)
    public boolean deliversInApp(String notificationCode) {
        return settings.find(notificationCode).map(NotificationSetting::isInAppEnabled).orElse(true);
    }

    /** Whether the notice has been muted entirely, which stops it being written anywhere. */
    @Transactional(readOnly = true)
    public boolean muted(String notificationCode) {
        return settings.find(notificationCode).map(row -> !row.isActive()).orElse(false);
    }

    private static String shortName(Throwable failed) {
        Throwable cause = failed;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + message.lines().findFirst().orElse("");
    }

    private static String clip(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /**
     * Whether an email was attempted at all, and what the transport said when it was. Callers that already
     * record the business event can ignore it; the delivery row holds the same facts for the administrator.
     */
    public record Outcome(boolean attempted, DeliveryStatus status, String detail) {
        public boolean delivered() { return status == DeliveryStatus.SENT; }
    }
}
