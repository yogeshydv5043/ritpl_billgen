package com.ritpl.billgen.api;

import java.time.Instant;

import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.PolicyType;

/** The content as stored, which is already the content that is safe to render. */
public record PolicyPageResponse(Long id, PolicyType policyType, String slug, String heading, String metaTitle,
        String metaDescription, String pageContent, ContentStatus status, boolean publiclyVisible,
        Instant updatedAt) { }
