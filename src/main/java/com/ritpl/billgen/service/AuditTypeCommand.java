package com.ritpl.billgen.service;

import com.ritpl.billgen.entity.AuditEventCategory;
import com.ritpl.billgen.entity.LimitPeriod;

/** Configuration of one audit definition: what is tracked, its quota and its retention. */
public record AuditTypeCommand(String code, String name, String description, AuditEventCategory eventCategory,
        String action, String resourceType, LimitPeriod limitPeriod, Integer usageLimit, Integer customPeriodDays,
        int retentionDays, int alertThresholdPercent, boolean active) { }
