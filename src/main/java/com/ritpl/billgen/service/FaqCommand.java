package com.ritpl.billgen.service;

import com.ritpl.billgen.entity.ContentStatus;

/** One question and its answer. The answer is HTML, sanitized on the way in. */
public record FaqCommand(Long faqTypeId, String question, String answer, Integer displayOrder,
        ContentStatus status) {
}
