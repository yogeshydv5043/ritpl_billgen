package com.ritpl.billgen.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InvoiceCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    public InvoiceTotals calculate(List<InvoiceLineInput> inputs) { return calculate(inputs, BigDecimal.ZERO); }

    /** Calculates every line server-side; a flat discount is applied after all line taxes. */
    public InvoiceTotals calculate(List<InvoiceLineInput> inputs, BigDecimal discountAmount) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 30) throw new IllegalArgumentException("Add between 1 and 30 invoice lines.");
        if (discountAmount == null) discountAmount = BigDecimal.ZERO;
        if (discountAmount.signum() < 0 || discountAmount.scale() > 2 || discountAmount.precision() > 14)
            throw new IllegalArgumentException("Discount must be non-negative with at most 2 decimal places.");
        BigDecimal subtotal = ZERO_MONEY;
        BigDecimal taxes = ZERO_MONEY;
        List<InvoiceTotals.CalculatedLine> lines = new ArrayList<>();
        for (InvoiceLineInput input : inputs) {
            if (input == null || input.description() == null || input.description().isBlank() || input.description().trim().length() > 240)
                throw new IllegalArgumentException("Each line needs a description of at most 240 characters.");
            BigDecimal quantity = input.quantity(); BigDecimal unitPrice = input.unitPrice();
            BigDecimal taxRate = input.taxRate() == null ? BigDecimal.ZERO : input.taxRate();
            if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 3 || quantity.precision() - quantity.scale() > 9)
                throw new IllegalArgumentException("Quantity must be positive, fit within 9 whole digits and have at most 3 decimal places.");
            if (unitPrice == null || unitPrice.signum() < 0 || unitPrice.scale() > 2 || unitPrice.precision() - unitPrice.scale() > 12)
                throw new IllegalArgumentException("Unit price must fit within 12 whole digits and have at most 2 decimal places.");
            if (taxRate.signum() < 0 || taxRate.compareTo(HUNDRED) > 0 || taxRate.scale() > 2)
                throw new IllegalArgumentException("Tax rate must be from 0 to 100 with at most 2 decimal places.");
            BigDecimal lineSubtotal = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTax = lineSubtotal.multiply(taxRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineSubtotal.add(lineTax).setScale(2, RoundingMode.HALF_UP);
            if (exceedsColumnLimit(lineSubtotal) || exceedsColumnLimit(lineTax) || exceedsColumnLimit(lineTotal))
                throw new IllegalArgumentException("A line total is larger than the supported invoice amount.");
            subtotal = subtotal.add(lineSubtotal); taxes = taxes.add(lineTax);
            if (exceedsColumnLimit(subtotal) || exceedsColumnLimit(taxes) || exceedsColumnLimit(subtotal.add(taxes)))
                throw new IllegalArgumentException("The invoice total is larger than the supported amount.");
            lines.add(new InvoiceTotals.CalculatedLine(input, lineSubtotal, lineTax, lineTotal));
        }
        BigDecimal gross = subtotal.add(taxes).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = discountAmount.setScale(2, RoundingMode.UNNECESSARY);
        if (discount.compareTo(gross) > 0) throw new IllegalArgumentException("Discount cannot exceed the pre-discount invoice total.");
        BigDecimal total = gross.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        return new InvoiceTotals(subtotal, taxes, discount, total, List.copyOf(lines));
    }

    private boolean exceedsColumnLimit(BigDecimal amount) { return amount.compareTo(new BigDecimal("999999999999.99")) > 0; }
}
