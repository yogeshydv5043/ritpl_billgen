package com.ritpl.billgen.api;

import com.ritpl.billgen.entity.PolicyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The HTML is sanitized by the service, so no pattern is applied to it here. */
public record PolicyPageRequest(@NotNull PolicyType policyType,
        @NotBlank @Size(max = 200) String heading,
        @Size(max = 200) String metaTitle,
        @Size(max = 500) String metaDescription,
        @NotBlank @Size(max = 20000) String pageContent,
        com.ritpl.billgen.entity.ContentStatus status) { }
