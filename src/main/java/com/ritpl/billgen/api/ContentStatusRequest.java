package com.ritpl.billgen.api;

import com.ritpl.billgen.entity.ContentStatus;
import jakarta.validation.constraints.NotNull;

/** Publishing and withdrawing are one field apart, so they share one request body. */
public record ContentStatusRequest(@NotNull ContentStatus status) { }
