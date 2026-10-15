package com.ritpl.billgen.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FaqTypeRequest(@NotBlank @Size(max = 150) String typeName,
        @Pattern(regexp = "^$|^[a-z0-9][a-z0-9-]{0,47}$", message = "must be lower-case letters, digits and hyphens")
        @Size(max = 48) String typeCode,
        @Size(max = 500) String description,
        @Min(0) @Max(9999) Integer displayOrder,
        com.ritpl.billgen.entity.ContentStatus status) { }
