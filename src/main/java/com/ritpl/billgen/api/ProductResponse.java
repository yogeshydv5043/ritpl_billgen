package com.ritpl.billgen.api;

import java.math.BigDecimal;
import com.ritpl.billgen.entity.CatalogProduct;
import com.ritpl.billgen.entity.ProductType;

public record ProductResponse(Long id,Long customerId,String customerName,String sku,String name,
        ProductType productType,Long categoryId,String category,String description,BigDecimal unitPrice,
        BigDecimal taxRate,boolean active) {
    public static ProductResponse from(CatalogProduct p){
        return new ProductResponse(p.getId(),p.getCustomerId(),p.getCustomer().getCompanyName(),p.getSku(),
                p.getName(),p.getProductType(),p.getCategory()==null?null:p.getCategory().getId(),
                p.getCategoryName(),p.getDescription(),p.getUnitPrice(),p.getTaxRate(),p.isActive());
    }
}