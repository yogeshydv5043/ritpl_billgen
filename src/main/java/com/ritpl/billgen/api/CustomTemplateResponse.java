package com.ritpl.billgen.api;

import com.ritpl.billgen.entity.CustomInvoiceTemplate;

public record CustomTemplateResponse(Long id, Long customerId, String name, String styleKey,
        String accentColor, boolean active) {
    public static CustomTemplateResponse from(CustomInvoiceTemplate template) {
        return new CustomTemplateResponse(template.getId(), template.getCustomerId(), template.getName(),
                template.getStyleKey(), template.getAccentColor(), template.isActive());
    }
}
