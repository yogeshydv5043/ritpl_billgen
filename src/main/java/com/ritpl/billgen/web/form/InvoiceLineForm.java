package com.ritpl.billgen.web.form;

import java.math.BigDecimal;
import com.ritpl.billgen.service.InvoiceLineInput;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class InvoiceLineForm {
    @NotBlank @Size(max = 240) private String description;
    @NotNull @DecimalMin(value = "0.001") @Digits(integer = 9, fraction = 3) private BigDecimal quantity = BigDecimal.ONE;
    @NotNull @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 2) private BigDecimal unitPrice = BigDecimal.ZERO;
    @NotNull @DecimalMin(value = "0.00") @DecimalMax(value = "100.00") @Digits(integer = 3, fraction = 2) private BigDecimal taxRate = new BigDecimal("18.00");

    public InvoiceLineInput toInput() { return new InvoiceLineInput(description, quantity, unitPrice, taxRate); }
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public BigDecimal getQuantity(){return quantity;} public void setQuantity(BigDecimal v){quantity=v;}
    public BigDecimal getUnitPrice(){return unitPrice;} public void setUnitPrice(BigDecimal v){unitPrice=v;}
    public BigDecimal getTaxRate(){return taxRate;} public void setTaxRate(BigDecimal v){taxRate=v;}
}
