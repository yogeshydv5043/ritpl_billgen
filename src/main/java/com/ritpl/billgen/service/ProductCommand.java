package com.ritpl.billgen.service;

import java.math.BigDecimal;
import com.ritpl.billgen.entity.ProductType;

public record ProductCommand(String sku, String name, ProductType productType, String category,
                             String description, BigDecimal unitPrice, BigDecimal taxRate, boolean active) { }
