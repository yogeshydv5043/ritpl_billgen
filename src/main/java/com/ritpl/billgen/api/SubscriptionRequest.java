package com.ritpl.billgen.api;

import java.time.LocalDate;
import com.ritpl.billgen.entity.CustomerSubscription;
import jakarta.validation.constraints.NotNull;

public record SubscriptionRequest(@NotNull Long planId) {

    public record SubscriptionResponse(Long id, Long customerId, Long planId,
                                       String planCode, String planName, String status, LocalDate startedOn,
                                       LocalDate endsOn, java.util.List<TemplateResponse> templates) {
        public static SubscriptionResponse from(CustomerSubscription s) {
            return new SubscriptionResponse(s.getId(), s.getCustomerId(),
                    s.getPlan().getId(), s.getPlan().getCode(), s.getPlan().getName(), s.getStatus().name(),
                    s.getStartedOn(), s.getEndsOn(),
                    s.getPlan().getTemplates().stream().filter(com.ritpl.billgen.entity.BillTemplate::isActive).map(TemplateResponse::from).toList());
        }
    }
}