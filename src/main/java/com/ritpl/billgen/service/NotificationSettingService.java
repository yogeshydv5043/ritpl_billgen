package com.ritpl.billgen.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.NotificationSetting;
import com.ritpl.billgen.repository.NotificationSettingRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * What the platform announces, and how loudly. The rows are seeded by the migration because a code
 * decides which job finds which setting: letting an administrator invent {@code BACKUP_FINISHED} would
 * create a notice nothing produces, and the page would promise something the system cannot do.
 *
 * <p>A missing row is not a failure for the jobs - it means "keep doing what the code already does" -
 * so {@link #active(String)} returns empty rather than throwing. Rows already in use can be muted but
 * not deleted, for the same reason a plan cannot be deleted while a subscription points at it.</p>
 */
@Service
public class NotificationSettingService {

    private final NotificationSettingRepository store;
    private final AuditService audit;

    public NotificationSettingService(NotificationSettingRepository store, AuditService audit) {
        this.store = store; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<NotificationSetting> list() { return store.findAllByOrderByStatusDescNotificationNameAsc(); }

    @Transactional(readOnly = true)
    public Optional<NotificationSetting> find(String code) {
        return store.findByNotificationCode(code == null ? "" : code.trim());
    }

    /** The row when the notice should reach somebody; empty when it is absent, muted or set to no channel. */
    @Transactional(readOnly = true)
    public Optional<NotificationSetting> active(String code) {
        return find(code).filter(NotificationSetting::deliversAnything);
    }

    @Transactional
    public NotificationSetting update(TenantScope scope, String code, NotificationSettingCommand command) {
        ConfigurationRules.requireAdmin(scope);
        NotificationSetting row = store.findByNotificationCode(code == null ? "" : code.trim())
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification code: " + code));
        Map<String, Object> before = Map.of("notificationName", row.getNotificationName(),
                "emailEnabled", row.isEmailEnabled(), "inAppEnabled", row.isInAppEnabled(),
                "triggerDaysBefore", row.getTriggerDaysBefore(), "repeatIntervalDays", row.getRepeatIntervalDays(),
                "status", row.getStatus().name());

        int daysBefore = ConfigurationRules.bounded(command.triggerDaysBefore(), row.getTriggerDaysBefore(), 0, 90,
                "Days before");
        int repeat = ConfigurationRules.bounded(command.repeatIntervalDays(), row.getRepeatIntervalDays(), 0, 365,
                "Repeat interval");
        boolean email = command.emailEnabled() == null ? row.isEmailEnabled() : command.emailEnabled();
        boolean inApp = command.inAppEnabled() == null ? row.isInAppEnabled() : command.inAppEnabled();
        ContentStatus status = command.status() == null ? row.getStatus() : command.status();
        if (status.isActive() && !email && !inApp) {
            throw new IllegalArgumentException("A notice that is switched on must reach somebody: choose at "
                    + "least one of email and in-app.");
        }
        row.recordEditor("administrator");
        row.setNotificationName(ConfigurationRules.required(command.notificationName(), 150, "Notification name"));
        row.setDescription(ConfigurationRules.optional(command.description(), 500, "Description"));
        row.setTriggerDaysBefore(daysBefore);
        row.setRepeatIntervalDays(repeat);
        row.setEmailEnabled(email);
        row.setInAppEnabled(inApp);
        row.setStatus(status);
        NotificationSetting saved = store.save(row);
        audit.record(AuditEventCommand.success(null, "UPDATE", "NOTIFICATION_SETTING", saved.getNotificationName(),
                saved.getId(), saved.getNotificationName() + " (" + saved.getNotificationCode() + "): "
                        + channels(saved) + ", " + timing(saved) + ".",
                before, Map.of("notificationName", saved.getNotificationName(), "emailEnabled", saved.isEmailEnabled(),
                        "inAppEnabled", saved.isInAppEnabled(), "triggerDaysBefore", saved.getTriggerDaysBefore(),
                        "repeatIntervalDays", saved.getRepeatIntervalDays(), "status", saved.getStatus().name())));
        return saved;
    }

    /** Switch a notice off without losing its configuration, so "stop telling people" is one click. */
    @Transactional
    public NotificationSetting setStatus(TenantScope scope, String code, ContentStatus status) {
        ConfigurationRules.requireAdmin(scope);
        NotificationSetting row = store.findByNotificationCode(code == null ? "" : code.trim())
                .orElseThrow(() -> new IllegalArgumentException("Unknown notification code: " + code));
        if (status.isActive() && !row.isEmailEnabled() && !row.isInAppEnabled()) {
            throw new IllegalArgumentException(row.getNotificationName() + " has no channel turned on, so "
                    + "switching it on would announce nothing.");
        }
        ContentStatus before = row.getStatus();
        row.recordEditor("administrator");
        row.setStatus(status);
        NotificationSetting saved = store.save(row);
        audit.record(AuditEventCommand.success(null, status.isActive() ? "ENABLE" : "DISABLE",
                "NOTIFICATION_SETTING", saved.getNotificationName(), saved.getId(),
                saved.getNotificationName() + (status.isActive() ? " switched on." : " switched off."),
                Map.of("status", before.name()), Map.of("status", status.name())));
        return saved;
    }

    private static String channels(NotificationSetting row) {
        if (!row.isActive()) return "switched off";
        if (row.isEmailEnabled() && row.isInAppEnabled()) return "email and in-app";
        if (row.isEmailEnabled()) return "email only";
        if (row.isInAppEnabled()) return "in-app only";
        return "no channel";
    }

    private static String timing(NotificationSetting row) {
        String when = row.getTriggerDaysBefore() == 0 ? "when it happens"
                : row.getTriggerDaysBefore() + " day(s) before";
        String repeat = row.getRepeatIntervalDays() == 0 ? "once while the condition lasts"
                : "again after " + row.getRepeatIntervalDays() + " day(s)";
        return when + ", " + repeat;
    }
}
