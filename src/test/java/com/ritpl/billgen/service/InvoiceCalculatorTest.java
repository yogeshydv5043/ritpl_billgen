package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceCalculatorTest {
    private final InvoiceCalculator calculator = new InvoiceCalculator();

    @Test
    void calculatesSubtotalTaxAndTotalPerLine() {
        InvoiceTotals totals = calculator.calculate(List.of(
                new InvoiceLineInput("Design workshop", new BigDecimal("2"), new BigDecimal("100.00"), new BigDecimal("18.00")),
                new InvoiceLineInput("Research", new BigDecimal("1.5"), new BigDecimal("40.00"), BigDecimal.ZERO)));

        assertEquals(new BigDecimal("260.00"), totals.subtotal());
        assertEquals(new BigDecimal("36.00"), totals.taxAmount());
        assertEquals(new BigDecimal("296.00"), totals.totalAmount());
        assertEquals(2, totals.lines().size());
    }

    @Test
    void roundsTaxHalfUpToCurrencyPrecision() {
        InvoiceTotals totals = calculator.calculate(List.of(
                new InvoiceLineInput("Small item", BigDecimal.ONE, new BigDecimal("0.05"), new BigDecimal("10.00"))));
        assertEquals(new BigDecimal("0.01"), totals.taxAmount());
        assertEquals(new BigDecimal("0.06"), totals.totalAmount());
    }

    @Test
    void appliesFlatDiscountAfterLineTaxes() {
        InvoiceTotals totals = calculator.calculate(List.of(
                new InvoiceLineInput("Taxed item", BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("18.00"))),
                new BigDecimal("10.00"));
        assertEquals(new BigDecimal("100.00"), totals.subtotal());
        assertEquals(new BigDecimal("18.00"), totals.taxAmount());
        assertEquals(new BigDecimal("10.00"), totals.discountAmount());
        assertEquals(new BigDecimal("108.00"), totals.totalAmount());
    }

    @Test
    void rejectsInvalidFlatDiscount() {
        var lines = List.of(new InvoiceLineInput("Item", BigDecimal.ONE, new BigDecimal("10.00"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(lines, new BigDecimal("10.01")));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(lines, new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(lines, new BigDecimal("0.001")));
    }

    @Test
    void requiresAtLeastOneLine() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(List.of()));
    }

    @Test
    void rejectsNegativePricesAndOutOfRangeTax() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(List.of(
                new InvoiceLineInput("Bad price", BigDecimal.ONE, new BigDecimal("-1.00"), BigDecimal.ZERO))));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(List.of(
                new InvoiceLineInput("Bad tax", BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("101.00")))));
    }
}
