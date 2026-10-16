package com.ritpl.billgen.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailTestRequest(@NotBlank @Email String recipient) { }
