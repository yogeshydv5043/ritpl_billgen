package com.ritpl.billgen.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(@NotBlank @Size(max = 150) String reviewerName,
        @NotBlank @Size(max = 2000) String reviewContent,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 120) String location,
        @Size(max = 120) String designation,
        @Size(max = 500) String profileImageUrl,
        @Min(0) @Max(9999) Integer displayOrder,
        com.ritpl.billgen.entity.ContentStatus status) { }
