package com.ritpl.billgen.entity;

public enum ProductType {
    PRODUCT("Product"), SERVICE("Service");

    private final String displayName;
    ProductType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
