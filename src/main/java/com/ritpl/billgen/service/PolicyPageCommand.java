package com.ritpl.billgen.service;

import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.PolicyType;

/** A policy's editable content. {@code pageContent} is HTML, sanitized on the way in rather than escaped. */
public record PolicyPageCommand(PolicyType policyType, String heading, String metaTitle,
        String metaDescription, String pageContent, ContentStatus status) {
}
