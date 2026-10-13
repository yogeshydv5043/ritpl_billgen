package com.ritpl.billgen.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritpl.billgen.entity.AuditRecord;
import com.ritpl.billgen.entity.AuditType;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.repository.AuditRecordRepository;
import com.ritpl.billgen.repository.AuditTypeRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.security.TenantScope;

/** Authorization-aware, append-only audit writer and reader. */
@Service
public class AuditService {
    private static final Set<String> SENSITIVE_KEYS = Set.of("password", "passwordhash", "secret", "token",
            "authorization", "credential", "apikey", "paymentcredential", "cardnumber", "cvv");
    /** How long the registry is remembered while tagging events; short, so edits take effect quickly. */
    private static final long TYPE_CACHE_TTL_MILLIS = 30_000L;

    private final AuditRecordRepository records;
    private final CustomerRepository customers;
    private final AuditTypeRepository auditTypes;
    private final ObjectMapper mapper;
    private volatile List<AuditType> knownTypes = List.of();
    private volatile long knownTypesLoadedAt;

    public AuditService(AuditRecordRepository records, CustomerRepository customers, AuditTypeRepository auditTypes,
            ObjectMapper mapper) {
        this.records = records; this.customers = customers; this.auditTypes = auditTypes; this.mapper = mapper;
    }

    @Transactional
    public AuditRecord record(AuditEventCommand command) {
        if (command == null) return null;
        Customer customer = command.customerId() == null ? null : customers.findById(command.customerId()).orElse(null);
        String action = required(command.action(), "SYSTEM", 48);
        String resourceType = required(command.resourceType(), "SYSTEM", 48);
        AuditRecord record = new AuditRecord(customer, clip(actor(command), 190), action, resourceType,
                clip(command.resourceName(), 160), clip(command.resourceId(), 80),
                "FAILURE".equals(command.outcome()) ? "FAILURE" : "SUCCESS",
                required(command.summary(), "Action recorded.", 1000), json(command.beforeValues()), json(command.afterValues()));
        // Tagging the event with the audit definition that governs it is what makes quotas, alerts and
        // retention configurable per event type. An unmatched event is still recorded, never dropped:
        // refusing to write an audit row would be the worse failure.
        record.setAuditTypeId(matchTypeId(action, resourceType));
        return records.save(record);
    }

    /** The audit definition that governs an event, read from a short-lived cache of the registry. */
    private Long matchTypeId(String action, String resourceType) {
        List<AuditType> types = activeTypes();
        // Naming one action is more specific than covering a whole resource type, so action rules
        // are tried first and a definition never depends on the registry's display order.
        for (AuditType type : types) {
            if (type.matchesAction(action)) return type.getId();
        }
        for (AuditType type : types) {
            if (type.matchesResourceType(resourceType)) return type.getId();
        }
        return null;
    }

    private List<AuditType> activeTypes() {
        long now = System.currentTimeMillis();
        if (now - knownTypesLoadedAt > TYPE_CACHE_TTL_MILLIS) {
            try {
                knownTypes = auditTypes.findByActiveTrueAndDeletedAtIsNullOrderByEventCategoryAscNameAsc();
            } catch (RuntimeException e) {
                knownTypes = List.of();            // never let a registry read break audit writing
            }
            knownTypesLoadedAt = now;
        }
        return knownTypes;
    }

    /** Drops the cached registry after an administrator edits it. */
    public void invalidateTypeCache() {
        knownTypesLoadedAt = 0L;
        knownTypes = List.of();
    }

    /** Persists failed attempts even when the surrounding business transaction is rolled back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditRecord recordFailure(AuditEventCommand command) { return record(command); }

    @Transactional
    public Page<AuditRecord> search(TenantScope scope, AuditFilter filter, Pageable pageable) {
        if (scope == null) throw new AccessDeniedException("Authentication is required.");
        AuditFilter f = filter == null ? new AuditFilter(null, null, null, null, null, null, null) : filter;
        Long customerId = f.customerId();
        if (!scope.isAdminScope()) {
            long ownId = scope.requireCustomerId();
            scope.requireCustomerAccess(customerId == null ? ownId : customerId);
            customerId = ownId;
        }
        Pageable safePage = pageable == null ? PageRequest.of(0, 50) : PageRequest.of(
                Math.max(pageable.getPageNumber(), 0), Math.min(Math.max(pageable.getPageSize(), 1), 200), pageable.getSort());
        Page<AuditRecord> page = records.search(customerId, f.actor(), normalize(f.action()),
                normalize(f.resourceType()), normalize(f.outcome()), f.from(), f.until(), safePage);
        Long tenant = scope.isAdminScope() ? customerId : scope.requireCustomerId();
        Map<String, Object> filterSummary = new LinkedHashMap<>();
        filterSummary.put("customerId", tenant);
        filterSummary.put("action", normalize(f.action()));
        filterSummary.put("resourceType", normalize(f.resourceType()));
        filterSummary.put("outcome", normalize(f.outcome()));
        filterSummary.put("actorFilterApplied", f.actor() != null);
        filterSummary.put("dateRangeApplied", f.from() != null || f.until() != null);
        record(AuditEventCommand.success(tenant, "VIEW", "AUDIT", "Audit records", null,
                "Authorized audit records were viewed.", null, filterSummary));
        return page;
    }

    @Transactional
    public AuditRecord findById(TenantScope scope, long id) {
        AuditRecord event = records.findWithCustomerById(id).orElseThrow(() -> new ResourceNotFoundException("Audit record not found."));
        if (!scope.isAdminScope()) scope.requireCustomerAccess(event.getCustomer() == null ? null : event.getCustomer().getId());
        Long tenant = scope.isAdminScope() ? (event.getCustomer() == null ? null : event.getCustomer().getId()) : scope.requireCustomerId();
        record(AuditEventCommand.success(tenant, "VIEW", "AUDIT", "Audit event", id,
                "Authorized audit event details were viewed.", null, Map.of("outcome", event.getOutcome(), "resourceType", event.getResourceType())));
        return event;
    }

    @Transactional
    public java.util.List<AuditRecord> forExport(TenantScope scope, AuditFilter filter) {
        // Export is deliberately bounded to protect memory and database resources.
        return search(scope, filter, PageRequest.of(0, 10000)).getContent();
    }

    private String actor(AuditEventCommand command) {
        if (command.actor() != null && !command.actor().isBlank()) return command.actor().trim();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getName() != null ? auth.getName() : "SYSTEM";
    }

    private String json(Map<String, ?> values) {
        if (values == null || values.isEmpty()) return null;
        try { return mapper.writeValueAsString(sanitize(values)); }
        catch (JsonProcessingException e) { return "{}"; }
    }

    private Map<String, Object> sanitize(Map<String, ?> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalized = key == null ? "" : key.replaceAll("[^A-Za-z]", "").toLowerCase(java.util.Locale.ROOT);
            if (SENSITIVE_KEYS.contains(normalized) || normalized.contains("password") || normalized.contains("secret")
                    || normalized.contains("token") || normalized.contains("credential") || normalized.contains("apikey")) return;
            if (value instanceof Map<?, ?> nested) {
                Map<String, Object> converted = new LinkedHashMap<>();
                nested.forEach((k, v) -> { if (k != null) converted.put(k.toString(), v); });
                result.put(key, sanitize(converted));
            } else if (value instanceof Number || value instanceof Boolean || value == null) {
                result.put(key, value);
            } else if (value instanceof Enum<?> e) {
                result.put(key, e.name());
            } else if (value instanceof java.time.temporal.TemporalAccessor) {
                result.put(key, value.toString());
            } else if (value instanceof Iterable<?> iterable) {
                result.put(key, java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                        .map(item -> item == null ? null : item.toString()).toList());
            } else {
                result.put(key, clip(value.toString(), 300));
            }
        });
        return result;
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT); }
    private String required(String value, String fallback, int max) { return clip(value == null || value.isBlank() ? fallback : value.trim(), max); }
    private String clip(String value, int max) { return value == null ? null : value.length() <= max ? value : value.substring(0, max); }
}
