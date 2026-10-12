package com.ritpl.billgen.api;

import jakarta.validation.constraints.Min;

public record SubscriptionLimitOverrideRequest(@Min(1) Integer invoiceLimit,
        @Min(1) Integer productLimit, @Min(0) Integer customTemplateLimit) { }
