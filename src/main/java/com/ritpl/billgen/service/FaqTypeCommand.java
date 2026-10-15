package com.ritpl.billgen.service;

import com.ritpl.billgen.entity.ContentStatus;

/** A FAQ grouping. A blank code is derived from the name, because the code is what links point at. */
public record FaqTypeCommand(String typeName, String typeCode, String description, Integer displayOrder,
        ContentStatus status) {
}
