package com.ritpl.billgen.api;

import java.time.LocalDateTime;
import com.ritpl.billgen.entity.ProductCategory;

public record CategoryResponse(Long id,Long customerId,String name,String description,LocalDateTime createdAt) {
    public static CategoryResponse from(ProductCategory c){
        return new CategoryResponse(c.getId(),c.getCustomerId(),c.getName(),c.getDescription(),c.getCreatedAt());
    }
}