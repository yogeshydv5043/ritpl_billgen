package com.ritpl.billgen.service;

import com.ritpl.billgen.entity.BillTemplateStyle;

/**
 * A reusable invoice layout. The text fields are the configurable blocks of the layout; a version
 * is bumped on every save so an already issued invoice keeps rendering what it was issued with.
 */
public record TemplateCommand(String code, String name, String description, BillTemplateStyle style,
                              String accentColor, boolean active, int sortOrder,
                              String headerText, String footerText, String paymentTerms, String termsAndConditions) {
    public TemplateCommand(String code, String name, String description, BillTemplateStyle style,
            String accentColor, boolean active, int sortOrder) {
        this(code, name, description, style, accentColor, active, sortOrder, null, null, null, null);
    }
}
