package com.ritpl.billgen.api;

import com.ritpl.billgen.entity.ContentStatus;

/** The number of questions is included because an administrator cannot delete a type that still has some. */
public record FaqTypeResponse(Long id, String typeName, String typeCode, String description, int displayOrder,
        ContentStatus status, long questionCount) { }
