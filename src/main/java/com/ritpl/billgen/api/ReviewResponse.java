package com.ritpl.billgen.api;

import java.time.Instant;

import com.ritpl.billgen.entity.ContentStatus;

public record ReviewResponse(Long id, String reviewerName, String reviewContent, int rating, String location,
        String designation, String profileImageUrl, int displayOrder, ContentStatus status, boolean deleted,
        Instant updatedAt, String updatedBy) { }
