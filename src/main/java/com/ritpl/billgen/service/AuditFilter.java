package com.ritpl.billgen.service;

import java.time.Instant;

public record AuditFilter(Long customerId, String actor, String action, String resourceType,
        String outcome, Instant from, Instant until) {
    public AuditFilter {
        actor = blankToNull(actor);
        action = blankToNull(action);
        resourceType = blankToNull(resourceType);
        outcome = blankToNull(outcome);
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
