package com.ritpl.billgen.service;

import com.ritpl.billgen.entity.BillTemplateStyle;

public record TemplateCommand(String code, String name, String description, BillTemplateStyle style,
                              String accentColor, boolean active, int sortOrder) { }