package com.ritpl.billgen.service;

import com.ritpl.billgen.entity.ContentStatus;

/** A testimonial as an administrator enters it. The rating is one to five, nothing else. */
public record ReviewCommand(String reviewerName, String reviewContent, Integer rating, String location,
        String designation, String profileImageUrl, Integer displayOrder, ContentStatus status) {
}
