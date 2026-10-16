package com.ritpl.billgen.service;

import java.util.Map;

/** Allow-listed business facts for a single append-only audit event. Never pass secrets here. */
public record AuditEventCommand(Long customerId, String actor, String action, String resourceType,
        String resourceName, String resourceId, String summary, Map<String, ?> beforeValues,
        Map<String, ?> afterValues, String outcome) {
    public static AuditEventCommand success(Long customerId, String action, String resourceType,
            String resourceName, Object resourceId, String summary, Map<String, ?> beforeValues,
            Map<String, ?> afterValues) {
        return new AuditEventCommand(customerId, null, action, resourceType, resourceName,
                resourceId == null ? null : resourceId.toString(), summary, beforeValues, afterValues, "SUCCESS");
    }
    public static AuditEventCommand failure(Long customerId, String action, String resourceType,
            String resourceName, Object resourceId, String summary, Map<String, ?> details) {
        return new AuditEventCommand(customerId, null, action, resourceType, resourceName,
                resourceId == null ? null : resourceId.toString(), summary, null, details, "FAILURE");
    }
    public AuditEventCommand withActor(String actorName) {
        return new AuditEventCommand(customerId, actorName, action, resourceType, resourceName, resourceId,
                summary, beforeValues, afterValues, outcome);
    }
}
