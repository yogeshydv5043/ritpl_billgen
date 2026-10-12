package com.ritpl.billgen.api;

import java.math.BigDecimal;
import com.ritpl.billgen.service.InvoiceLineInput;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InvoiceLineRequest(@NotBlank @Size(max=240) String description,
        @NotNull @DecimalMin("0.001") @Digits(integer=9,fraction=3) BigDecimal quantity,
        @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal unitPrice,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer=3,fraction=2) BigDecimal taxRate,
        Long productId) {
    public InvoiceLineInput toInput(){return new InvoiceLineInput(description,quantity,unitPrice,taxRate,productId);}
}
