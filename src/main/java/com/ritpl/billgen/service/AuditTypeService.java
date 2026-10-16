package com.ritpl.billgen.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.AuditType;
import com.ritpl.billgen.entity.LimitPeriod;
import com.ritpl.billgen.repository.AuditRecordRepository;
import com.ritpl.billgen.repository.AuditTypeRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * The audit <em>configuration</em> module: which activities the application tracks, how often a
 * plan may produce them and how long that history is kept. History itself is written by
 * {@link AuditService} into append-only rows; nothing here can rewrite it.
 */
@Service
public class AuditTypeService {
    private static final Logger log = LoggerFactory.getLogger(AuditTypeService.class);

    private final AuditTypeRepository types;
    private final AuditRecordRepository records;
    private final AuditService audit;
    @PersistenceContext private EntityManager entityManager;

    public AuditTypeService(AuditTypeRepository types, AuditRecordRepository records, AuditService audit) {
        this.types = types; this.records = records; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AuditType> findAll() { return types.findByDeletedAtIsNullOrderByEventCategoryAscNameAsc(); }

    @Transactional(readOnly = true)
    public List<AuditType> findActive() { return types.findByActiveTrueAndDeletedAtIsNullOrderByEventCategoryAscNameAsc(); }

    @Transactional(readOnly = true)
    public AuditType findById(long id) {
        return types.findById(id).filter(t -> !t.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Audit type not found."));
    }

    @Transactional(readOnly = true)
    public long activeCount() { return types.countByActiveTrueAndDeletedAtIsNull(); }

    /** Rows for the audit type listing, each annotated with how many plans permit it. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> overview() {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (AuditType type : findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", type);
            row.put("planCount", type.getId() == null ? 0 : types.countPlansUsing(type.getId()));
            row.put("recordCount", type.getId() == null ? 0
                    : records.countByAuditTypeIdAndCreatedAtAfter(type.getId(), Instant.EPOCH));
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public AuditType save(TenantScope scope, Long id, AuditTypeCommand command) {
        requireAdmin(scope);
        validate(command, id);
        String code = command.code().trim().toUpperCase(Locale.ROOT);
        AuditType type = id == null ? new AuditType(code, command.name().trim(), blankToNull(command.description()),
                command.eventCategory(), blankToNull(command.action()), blankToNull(command.resourceType())) : findById(id);
        Map<String, Object> before = id == null ? null : Map.of("name", type.getName(), "active", type.isActive(),
                "limit", type.getUsageLimit() == null ? "none" : type.getUsageLimit());
        type.setName(command.name().trim());
        type.setDescription(blankToNull(command.description()));
        type.setEventCategory(command.eventCategory());
        type.setAction(blankToNull(command.action()));
        type.setResourceType(blankToNull(command.resourceType()));
        type.setLimitPeriod(command.limitPeriod());
        type.setUsageLimit(command.usageLimit());
        type.setCustomPeriodDays(command.customPeriodDays());
        type.setRetentionDays(command.retentionDays());
        type.setAlertThresholdPercent(command.alertThresholdPercent());
        type.setActive(command.active());
        AuditType saved = types.save(type);
        audit.record(AuditEventCommand.success(null, id == null ? "CREATE" : "UPDATE", "AUDIT_TYPE", "Audit type",
                saved.getId(), id == null ? "Administrator created an audit type." : "Administrator updated an audit type.",
                before, Map.of("name", saved.getName(), "period", saved.getLimitPeriod().name(),
                        "limit", saved.getUsageLimit() == null ? "none" : saved.getUsageLimit(),
                        "retentionDays", saved.getRetentionDays())));
        audit.invalidateTypeCache();      // events are tagged from the next write onwards
        return saved;
    }

    @Transactional
    public AuditType setActive(TenantScope scope, long id, boolean active) {
        requireAdmin(scope);
        AuditType type = findById(id);
        boolean before = type.isActive();
        type.setActive(active);
        audit.record(AuditEventCommand.success(null, active ? "ACTIVATE" : "DEACTIVATE", "AUDIT_TYPE", "Audit type", id,
                "Administrator changed whether an activity is tracked.", Map.of("active", before), Map.of("active", active)));
        audit.invalidateTypeCache();
        return type;
    }

    /** Soft delete, refused while a plan still grants the definition. */
    @Transactional
    public void delete(TenantScope scope, long id, String actor) {
        requireAdmin(scope);
        AuditType type = findById(id);
        long usedBy = type.getId() == null ? 0 : types.countPlansUsing(type.getId());
        if (usedBy > 0) {
            throw new IllegalArgumentException("This audit type is included in " + usedBy
                    + " plan(s). Remove it from those plans before deleting it.");
        }
        type.setDeletedAt(java.time.LocalDateTime.now());
        type.setDeletedBy(actor);
        type.setActive(false);
        audit.record(AuditEventCommand.success(null, "DELETE", "AUDIT_TYPE", "Audit type", id,
                "Administrator deleted an audit type definition. Existing history is retained.",
                Map.of("name", type.getName()), Map.of("deleted", true)));
        audit.invalidateTypeCache();
    }

    /** Usage of one definition for one account inside its quota window. */
    @Transactional(readOnly = true)
    public Map<String, Object> usage(AuditType type, Long customerId) {
        Map<String, Object> result = new LinkedHashMap<>();
        int days = type.quotaWindowDays();
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);
        long used = type.getId() == null ? 0 : (customerId == null
                ? records.countByAuditTypeIdAndCreatedAtAfter(type.getId(), from)
                : records.countByAuditTypeIdAndCustomerIdAndCreatedAtAfter(type.getId(), customerId, from));
        Integer limit = type.getUsageLimit();
        result.put("used", used);
        result.put("limit", limit);
        result.put("windowDays", type.hasQuota() ? days : 0);
        result.put("percent", limit == null || limit <= 0 ? 0 : (int) Math.min(100, Math.round(used * 100.0 / limit)));
        result.put("warning", limit != null && limit > 0 && used * 100L >= limit * (long) type.getAlertThresholdPercent());
        result.put("exhausted", limit != null && limit > 0 && used >= limit);
        return result;
    }

    /**
     * Applies each definition's retention period. Deletion only succeeds because the database
     * trigger recognises a purge session that declares its cutoff, and even then it can only remove
     * rows at or before that cutoff - so an application bug cannot destroy recent history.
     */
    @Transactional
    public Map<String, Integer> purgeExpiredHistory() {
        Instant now = Instant.now();
        Map<String, Integer> perType = new LinkedHashMap<>();
        int total = 0;
        for (AuditType type : findAll()) {
            if (type.getRetentionDays() <= 0) continue;
            Instant cutoff = now.minus(type.getRetentionDays(), ChronoUnit.DAYS);
            int deleted = purgeOlderThan(type.getId(), cutoff);
            if (deleted > 0) {
                perType.put(type.getName(), deleted);
                total += deleted;
            }
        }
        if (total > 0) {
            audit.record(AuditEventCommand.success(null, "RETENTION_PURGE", "AUDIT_TYPE", "Audit history", null,
                            "Audit history older than the configured retention was removed.", null,
                            Map.of("removed", total)).withActor("SYSTEM"));
        }
        perType.put("TOTAL", total);
        log.info("Audit retention purge removed {} record(s)", total);
        return perType;
    }

    /** Deletes one definition's expired rows and returns how many were removed. */
    private int purgeOlderThan(Long auditTypeId, Instant cutoff) {
        if (auditTypeId == null) return 0;
        entityManager.flush();
        // A transaction-local setting: the append-only trigger only allows DELETE for the duration
        // of this transaction, and only for rows created at or before the declared cutoff.
        entityManager.createNativeQuery("select set_config('app.audit_purge_cutoff', :cutoff, true)")
                .setParameter("cutoff", cutoff.toString()).getSingleResult();
        int deleted = entityManager.createNativeQuery(
                        "delete from audit_records where audit_type_id = :typeId and created_at <= :cutoff")
                .setParameter("typeId", auditTypeId).setParameter("cutoff", cutoff).executeUpdate();
        entityManager.clear();
        return deleted;
    }

    private void validate(AuditTypeCommand command, Long id) {
        if (command == null) throw new IllegalArgumentException("Enter the audit type details.");
        if (command.code() == null || command.code().isBlank() || !command.code().trim().matches("[A-Za-z0-9_-]{3,48}")) {
            throw new IllegalArgumentException("A code may use 3-48 letters, digits, dashes and underscores.");
        }
        String code = command.code().trim().toUpperCase(Locale.ROOT);
        boolean duplicateCode = id == null ? types.existsByCodeIgnoreCase(code) : types.existsByCodeIgnoreCaseAndIdNot(code, id);
        if (duplicateCode) throw new IllegalArgumentException("That audit type code is already in use.");
        if (command.name() == null || command.name().isBlank() || command.name().trim().length() > 120) {
            throw new IllegalArgumentException("Enter an audit type name of at most 120 characters.");
        }
        boolean duplicateName = id == null ? types.existsByNameIgnoreCase(command.name().trim())
                : types.existsByNameIgnoreCaseAndIdNot(command.name().trim(), id);
        if (duplicateName) throw new IllegalArgumentException("An audit type with this name already exists.");
        if (command.eventCategory() == null) throw new IllegalArgumentException("Choose an event category.");
        if (isBlank(command.action()) && isBlank(command.resourceType())) {
            throw new IllegalArgumentException("Match this type to an action or a resource type so events can be attributed.");
        }
        LimitPeriod period = command.limitPeriod() == null ? LimitPeriod.NONE : command.limitPeriod();
        if (period == LimitPeriod.CUSTOM && (command.customPeriodDays() == null
                || command.customPeriodDays() < 1 || command.customPeriodDays() > 365)) {
            throw new IllegalArgumentException("A custom limit period needs a length between 1 and 365 days.");
        }
        if (period != LimitPeriod.NONE && (command.usageLimit() == null || command.usageLimit() <= 0)) {
            throw new IllegalArgumentException("Set a positive usage limit, or choose \"No limit\" as the period.");
        }
        if (command.retentionDays() < 1) throw new IllegalArgumentException("Retention must be at least one day.");
        if (command.alertThresholdPercent() < 1 || command.alertThresholdPercent() > 100) {
            throw new IllegalArgumentException("The alert threshold must be between 1 and 100 percent.");
        }
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
    private String blankToNull(String value) { return isBlank(value) ? null : value.trim(); }

    private void requireAdmin(TenantScope scope) {
        if (scope == null || !scope.isAdminScope()) {
            throw new org.springframework.security.access.AccessDeniedException("Administrator access is required.");
        }
    }
}
