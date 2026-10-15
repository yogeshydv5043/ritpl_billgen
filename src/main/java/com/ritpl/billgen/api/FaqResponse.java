package com.ritpl.billgen.api;

import java.time.Instant;

import com.ritpl.billgen.entity.ContentStatus;

public record FaqResponse(Long id, Long faqTypeId, String faqTypeCode, String faqTypeName, String question,
        String answer, int displayOrder, ContentStatus status, Instant updatedAt) { }
