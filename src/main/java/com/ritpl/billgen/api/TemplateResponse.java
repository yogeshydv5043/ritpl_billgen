package com.ritpl.billgen.api;

import com.ritpl.billgen.entity.BillTemplate;

public record TemplateResponse(Long id,String code,String name,String description,String style,
        String accentColor,boolean active,int sortOrder) {
    public static TemplateResponse from(BillTemplate t){
        return new TemplateResponse(t.getId(),t.getCode(),t.getName(),t.getDescription(),t.getStyleCode(),
                t.getAccentColor(),t.isActive(),t.getSortOrder());
    }
}