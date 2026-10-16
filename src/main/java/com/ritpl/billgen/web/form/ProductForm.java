package com.ritpl.billgen.web.form;

import java.math.BigDecimal;
import com.ritpl.billgen.entity.CatalogProduct;
import com.ritpl.billgen.entity.ProductType;
import com.ritpl.billgen.service.ProductCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProductForm {
    @NotBlank @Size(max=50) @Pattern(regexp="[A-Za-z0-9._-]+") private String sku;
    @NotBlank @Size(max=160) private String name;
    @NotNull private ProductType productType = ProductType.SERVICE;
    private Long categoryId;
    @Size(max=500) private String description;
    @Size(max=24) private String unit;
    @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) private BigDecimal unitPrice = BigDecimal.ZERO;
    @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer=3,fraction=2) private BigDecimal taxRate = new BigDecimal("18.00");
    private boolean active = true;

    public static ProductForm from(CatalogProduct p) {
        ProductForm f = new ProductForm();
        f.sku = p.getSku(); f.name = p.getName(); f.productType = p.getProductType();
        f.categoryId = p.getCategory() == null ? null : p.getCategory().getId();
        f.description = p.getDescription(); f.unitPrice = p.getUnitPrice(); f.taxRate = p.getTaxRate(); f.active = p.isActive();
        f.unit = p.getUnit();
        return f;
    }

    public ProductCommand toCommand() {
        return new ProductCommand(sku, name, productType, categoryId, description, unitPrice, taxRate, active, unit);
    }

    public String getSku(){return sku;} public void setSku(String v){sku=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public ProductType getProductType(){return productType;} public void setProductType(ProductType v){productType=v;}
    public Long getCategoryId(){return categoryId;} public void setCategoryId(Long v){categoryId=v;}
    public String getUnit(){return unit;} public void setUnit(String v){unit=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public BigDecimal getUnitPrice(){return unitPrice;} public void setUnitPrice(BigDecimal v){unitPrice=v;}
    public BigDecimal getTaxRate(){return taxRate;} public void setTaxRate(BigDecimal v){taxRate=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}