package com.ritpl.billgen.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(@NotBlank @Size(max=120) String name, @Size(max=300) String description) { }