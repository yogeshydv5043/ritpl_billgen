package com.ritpl.billgen.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FaqRequest(@NotNull Long faqTypeId,
        @NotBlank @Size(max = 500) String question,
        @NotBlank @Size(max = 4000) String answer,
        @Min(0) @Max(9999) Integer displayOrder,
        com.ritpl.billgen.entity.ContentStatus status) { }
