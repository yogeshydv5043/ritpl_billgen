package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.ritpl.billgen.entity.AuditRecord;
import com.ritpl.billgen.entity.CatalogProduct;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.entity.ProductType;

class ExcelExportServiceTest {

    private final ExcelExportService exports = new ExcelExportService();
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void monthlyReportCoversExactlyTenCalendarMonthsIncludingCurrent() throws IOException {
        YearMonth current = YearMonth.of(2026, 10);
        List<Invoice> invoices = List.of(invoice("INV-2026-0001", InvoiceStatus.SENT, "100.00", "2026-10-05T10:00:00Z", true),
                invoice("INV-2026-0002", InvoiceStatus.SENT, "50.00", "2026-01-15T10:00:00Z", true));

        try (XSSFWorkbook workbook = read(exports.monthlyInvoices(invoices, current, UTC, quota(), Instant.parse("2026-10-12T06:00:00Z")))) {
            assertEquals(List.of("Monthly Summary", "Financial Summary", "Invoice Details", "Report Info"), sheetNames(workbook));
            Sheet summary = workbook.getSheet("Monthly Summary");
            assertEquals(10, summary.getLastRowNum(), "ten calendar months plus a header row");
            assertEquals("2026-01", text(summary, 1, 0), "oldest month in the window");
            assertEquals("2026-10", text(summary, 10, 0), "current month is included");
            assertEquals(1.0, number(summary, 1, 1), "issue count for January 2026");
            assertEquals(1.0, number(summary, 10, 1), "issue count for the current month");
            assertEquals("2026-01 through 2026-10 (10 calendar months, inclusive)", text(workbook.getSheet("Report Info"), 2, 1));
        }
    }

    @Test
    void monthlyReportExcludesInvoicesOutsideTheWindowFromEverySheet() throws IOException {
        YearMonth current = YearMonth.of(2026, 10);
        List<Invoice> invoices = List.of(
                invoice("INV-IN-WINDOW", InvoiceStatus.SENT, "10.00", "2026-10-01T00:00:00Z", true),
                invoice("INV-TOO-OLD", InvoiceStatus.SENT, "10.00", "2025-12-31T23:59:59Z", true));

        try (XSSFWorkbook workbook = read(exports.monthlyInvoices(invoices, current, UTC, quota(), Instant.parse("2026-10-12T06:00:00Z")))) {
            Sheet details = workbook.getSheet("Invoice Details");
            assertEquals(1, details.getLastRowNum(), "only invoices inside the window are listed");
            assertEquals("INV-IN-WINDOW", text(details, 1, 0));
            assertEquals("1", text(workbook.getSheet("Report Info"), 5, 1), "detail-row count follows the window");
        }
    }

    @Test
    void monthlyReportAssignsMonthsUsingTheConfiguredTimezone() throws IOException {
        YearMonth current = YearMonth.of(2026, 10);
        // 2026-09-30T18:30:00Z is still September in UTC but already 1 October in Asia/Kolkata.
        List<Invoice> invoices = List.of(invoice("INV-BOUNDARY", InvoiceStatus.SENT, "10.00", "2026-09-30T18:30:00Z", true));

        try (XSSFWorkbook workbook = read(exports.monthlyInvoices(invoices, current, UTC, quota(), Instant.parse("2026-10-12T06:00:00Z")))) {
            Sheet utc = workbook.getSheet("Monthly Summary");
            assertEquals(1.0, number(utc, 9, 1), "counted in September 2026 under UTC");
            assertEquals(0.0, number(utc, 10, 1));
        }
        try (XSSFWorkbook workbook = read(exports.monthlyInvoices(invoices, current, ZoneId.of("Asia/Kolkata"), quota(), Instant.parse("2026-10-12T06:00:00Z")))) {
            Sheet ist = workbook.getSheet("Monthly Summary");
            assertEquals(0.0, number(ist, 9, 1));
            assertEquals(1.0, number(ist, 10, 1), "counted in October 2026 under Asia/Kolkata");
        }
    }

    @Test
    void monthlyReportKeepsGenerationCountsSeparateFromFinancialTotals() throws IOException {
        YearMonth current = YearMonth.of(2026, 10);
        List<Invoice> invoices = List.of(
                invoice("INV-DRAFT", InvoiceStatus.DRAFT, "500.00", "2026-10-02T09:00:00Z", false),
                invoice("INV-SENT", InvoiceStatus.SENT, "100.00", "2026-10-03T09:00:00Z", true),
                invoice("INV-PAID", InvoiceStatus.PAID, "50.00", "2026-10-04T09:00:00Z", true),
                invoice("INV-CANCELLED", InvoiceStatus.CANCELLED, "999.00", "2026-10-05T09:00:00Z", true));

        try (XSSFWorkbook workbook = read(exports.monthlyInvoices(invoices, current, UTC, quota(), Instant.parse("2026-10-12T06:00:00Z")))) {
            Sheet summary = workbook.getSheet("Monthly Summary");
            assertEquals(3.0, number(summary, 10, 1), "drafts do not consume the allowance, so only issued invoices are counted");
            assertEquals(1.0, number(summary, 10, 2), "draft count is reported separately");

            Sheet financial = workbook.getSheet("Financial Summary");
            assertEquals("2026-10", text(financial, 1, 0));
            assertEquals(1.0, number(financial, 1, 2), "sent invoice count");
            assertEquals(1.0, number(financial, 1, 3), "paid invoice count");
            assertEquals(100.0, number(financial, 1, 4), "sent amount");
            assertEquals(50.0, number(financial, 1, 5), "paid amount");
            assertEquals(150.0, number(financial, 1, 6), "cancelled values are never added to financial totals");
        }
    }

    @Test
    void productExportNeutralisesFormulaInjection() throws IOException {
        CatalogProduct product = new CatalogProduct(null, "=cmd|'/c calc'!A0", "+HYPERLINK(\"http://evil.test\")",
                ProductType.SERVICE, new BigDecimal("10.00"), new BigDecimal("5.00"));
        product.setDescription("@SUM(1+1)*cmd");

        try (XSSFWorkbook workbook = read(exports.products(List.of(product)))) {
            Sheet sheet = workbook.getSheet("Products");
            assertEquals("'=cmd|'/c calc'!A0", text(sheet, 1, 0));
            assertEquals("'+HYPERLINK(\"http://evil.test\")", text(sheet, 1, 1));
            assertEquals("'@SUM(1+1)*cmd", text(sheet, 1, 4));
        }
    }

    @Test
    void productExportKeepsOrdinaryTextUnchanged() throws IOException {
        CatalogProduct product = new CatalogProduct(null, "SKU-1", "Design workshop", ProductType.SERVICE,
                new BigDecimal("100.00"), new BigDecimal("18.00"));
        product.setDescription("Half-day on-site workshop");

        try (XSSFWorkbook workbook = read(exports.products(List.of(product)))) {
            Sheet sheet = workbook.getSheet("Products");
            assertEquals("SKU-1", text(sheet, 1, 0));
            assertEquals("Design workshop", text(sheet, 1, 1));
            assertEquals("Service", text(sheet, 1, 2));
            assertEquals("Half-day on-site workshop", text(sheet, 1, 4));
            assertEquals(100.0, number(sheet, 1, 5));
            assertEquals(18.0, number(sheet, 1, 6));
        }
    }

    @Test
    void auditExportNeutralisesFormulaInjection() throws IOException {
        AuditRecord record = new AuditRecord(null, "=IMPORTXML(\"http://evil.test\")", "=1+1", "-2+3+cmd|' /C calc'!A0",
                "Product", "INV-1", "SUCCESS", "=cmd|'/c calc'!A0", "=before", "=after");

        try (XSSFWorkbook workbook = read(exports.audit(List.of(record)))) {
            Sheet sheet = workbook.getSheet("Audit Records");
            assertTrue(text(sheet, 1, 2).startsWith("'"), "actor column is formula protected");
            assertTrue(text(sheet, 1, 3).startsWith("'"), "action column is formula protected");
            assertTrue(text(sheet, 1, 8).startsWith("'"), "summary column is formula protected");
            assertTrue(text(sheet, 1, 10).startsWith("'"), "after-values column is formula protected");
            assertEquals("'=cmd|'/c calc'!A0", text(sheet, 1, 8),
                    "untrusted summary text is stored as literal text, never as a live formula");
        }
    }

    private static Invoice invoice(String number, InvoiceStatus status, String total, String generatedAt, boolean usageCounted) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(number);
        invoice.setStatus(status);
        invoice.setCurrency("INR");
        invoice.setIssueDate(LocalDate.of(2026, 10, 1));
        invoice.setDueDate(LocalDate.of(2026, 10, 31));
        invoice.setSubtotal(new BigDecimal(total));
        invoice.setTaxAmount(BigDecimal.ZERO);
        invoice.setDiscountAmount(BigDecimal.ZERO);
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setGeneratedAt(Instant.parse(generatedAt));
        invoice.setUsageCounted(usageCounted);
        invoice.setCompanyNameSnapshot("Acme Traders");
        return invoice;
    }

    private static UsageQuota quota() {
        return new UsageQuota(100L, 40L, 60L, 50L, 5L, 45L, 2L, 1L, 1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    }

    private static XSSFWorkbook read(byte[] workbook) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(workbook));
    }

    private static java.util.List<String> sheetNames(XSSFWorkbook workbook) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) names.add(workbook.getSheetName(i));
        return names;
    }

    private static String text(Sheet sheet, int row, int column) {
        Row r = sheet.getRow(row);
        return r == null ? "" : r.getCell(column).getStringCellValue();
    }

    private static double number(Sheet sheet, int row, int column) {
        return sheet.getRow(row).getCell(column).getNumericCellValue();
    }
}
