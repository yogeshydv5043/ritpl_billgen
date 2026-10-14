package com.ritpl.billgen.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import com.ritpl.billgen.entity.AuditRecord;
import com.ritpl.billgen.entity.CatalogProduct;
import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceStatus;

/** Excel export writer. All untrusted values are emitted as explicit string cells. */
@Service
public class ExcelExportService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    public byte[] products(List<CatalogProduct> products) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet("Products");
            List<String> headers = List.of("SKU", "Name", "Type", "Category", "Description", "Unit Price", "Tax Rate (%)", "Active");
            header(sheet, headers, styles.header()); int rowNum = 1;
            for (CatalogProduct p : products) {
                Row row = sheet.createRow(rowNum++); int c = 0;
                text(row, c++, p.getSku(), styles.text()); text(row, c++, p.getName(), styles.text());
                text(row, c++, p.getProductType() == null ? "" : p.getProductType().getDisplayName(), styles.text());
                text(row, c++, p.getCategoryName(), styles.text()); text(row, c++, p.getDescription(), styles.text());
                decimal(row, c++, p.getUnitPrice(), styles.money()); decimal(row, c++, p.getTaxRate(), styles.money());
                text(row, c, p.isActive() ? "Yes" : "No", styles.text());
            }
            filterAndFreeze(sheet, rowNum, headers.size()); widths(sheet, 18, 32, 16, 22, 48, 16, 18, 12);
            workbook.write(out); workbook.dispose(); return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Could not create the product workbook.", e); }
    }

    public byte[] monthlyInvoices(List<Invoice> invoices, YearMonth currentMonth, ZoneId zone, UsageQuota quota, Instant generatedAt) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            TreeMap<YearMonth, List<Invoice>> months = new TreeMap<>();
            for (int i = 9; i >= 0; i--) months.put(currentMonth.minusMonths(i), new ArrayList<>());
            // Only invoices inside the reporting window may appear on any sheet of this report.
            List<Invoice> windowed = new ArrayList<>();
            for (Invoice invoice : invoices) {
                if (invoice.getGeneratedAt() == null) continue;
                YearMonth month = YearMonth.from(invoice.getGeneratedAt().atZone(zone));
                if (months.containsKey(month)) { months.get(month).add(invoice); windowed.add(invoice); }
            }

            Sheet summary = workbook.createSheet("Monthly Summary");
            List<String> summaryHeaders = List.of("Month", "Invoice Generation Count (Issued)", "Draft Count (Not Counted)", "Cancelled Count");
            header(summary, summaryHeaders, styles.header()); int rowNum = 1;
            for (Map.Entry<YearMonth, List<Invoice>> entry : months.entrySet()) {
                long issued = entry.getValue().stream().filter(Invoice::isUsageCounted).count();
                long drafts = entry.getValue().stream().filter(i -> i.getStatus() == InvoiceStatus.DRAFT).count();
                long cancelled = entry.getValue().stream().filter(i -> i.getStatus() == InvoiceStatus.CANCELLED).count();
                Row row = summary.createRow(rowNum++); text(row, 0, entry.getKey().toString(), styles.text());
                number(row, 1, issued); number(row, 2, drafts); number(row, 3, cancelled);
            }
            filterAndFreeze(summary, rowNum, summaryHeaders.size()); widths(summary, 18, 36, 30, 24);

            Sheet financial = workbook.createSheet("Financial Summary");
            List<String> financialHeaders = List.of("Month", "Currency", "Sent Invoice Count", "Paid Invoice Count", "Sent Amount", "Paid Amount", "Total Sent / Paid Amount");
            header(financial, financialHeaders, styles.header());
            TreeMap<YearMonth, TreeMap<String, List<Invoice>>> groups = new TreeMap<>();
            for (Map.Entry<YearMonth, List<Invoice>> month : months.entrySet()) {
                for (Invoice invoice : month.getValue()) {
                    if (invoice.getStatus() != InvoiceStatus.SENT && invoice.getStatus() != InvoiceStatus.PAID) continue;
                    String currency = invoice.getCurrency() == null ? "" : invoice.getCurrency();
                    groups.computeIfAbsent(month.getKey(), ignored -> new TreeMap<>())
                            .computeIfAbsent(currency, ignored -> new ArrayList<>()).add(invoice);
                }
            }
            rowNum = 1;
            for (Map.Entry<YearMonth, TreeMap<String, List<Invoice>>> month : groups.entrySet()) {
                for (Map.Entry<String, List<Invoice>> currency : month.getValue().entrySet()) {
                    long sentCount = currency.getValue().stream().filter(i -> i.getStatus() == InvoiceStatus.SENT).count();
                    long paidCount = currency.getValue().stream().filter(i -> i.getStatus() == InvoiceStatus.PAID).count();
                    BigDecimal sent = totalFor(currency.getValue(), InvoiceStatus.SENT);
                    BigDecimal paid = totalFor(currency.getValue(), InvoiceStatus.PAID);
                    Row row = financial.createRow(rowNum++); int c = 0;
                    text(row, c++, month.getKey().toString(), styles.text()); text(row, c++, currency.getKey(), styles.text());
                    number(row, c++, sentCount); number(row, c++, paidCount); decimal(row, c++, sent, styles.money());
                    decimal(row, c++, paid, styles.money()); decimal(row, c, sent.add(paid), styles.money());
                }
            }
            filterAndFreeze(financial, rowNum, financialHeaders.size()); widths(financial, 18, 14, 22, 22, 20, 20, 30);

            Sheet details = workbook.createSheet("Invoice Details");
            List<String> detailHeaders = List.of("Invoice Number", "Generated At", "Company Snapshot", "Template Snapshot", "Status", "Issue Date", "Due Date",
                    "Currency", "Subtotal", "Tax", "Discount", "Total", "Allowance Counted");
            header(details, detailHeaders, styles.header()); rowNum = 1;
            for (Invoice invoice : windowed) {
                Row row = details.createRow(rowNum++); int c = 0;
                text(row, c++, invoice.getInvoiceNumber(), styles.text());
                text(row, c++, invoice.getGeneratedAt() == null ? "" : TIME.format(invoice.getGeneratedAt().atZone(zone)), styles.text());
                text(row, c++, invoice.getCompanyNameSnapshot() == null ? invoice.getCustomer().getCompanyName() : invoice.getCompanyNameSnapshot(), styles.text());
                text(row, c++, invoice.getTemplateName(), styles.text()); text(row, c++, invoice.getStatus().name(), styles.text());
                text(row, c++, invoice.getIssueDate() == null ? "" : invoice.getIssueDate().toString(), styles.text());
                text(row, c++, invoice.getDueDate() == null ? "" : invoice.getDueDate().toString(), styles.text());
                text(row, c++, invoice.getCurrency(), styles.text()); decimal(row, c++, invoice.getSubtotal(), styles.money());
                decimal(row, c++, invoice.getTaxAmount(), styles.money()); decimal(row, c++, invoice.getDiscountAmount(), styles.money());
                decimal(row, c++, invoice.getTotalAmount(), styles.money()); text(row, c, invoice.isUsageCounted() ? "Yes" : "No", styles.text());
            }
            filterAndFreeze(details, rowNum, detailHeaders.size()); widths(details, 22, 28, 34, 24, 16, 18, 18, 14, 18, 18, 18, 18, 20);

            Sheet info = workbook.createSheet("Report Info");
            List<String[]> metadata = List.of(
                    new String[] {"Report", "Monthly invoice usage and financial summary"},
                    new String[] {"Window", currentMonth.minusMonths(9) + " through " + currentMonth + " (10 calendar months, inclusive)"},
                    new String[] {"Timezone", zone.getId()},
                    new String[] {"Generated at", TIME.format(generatedAt.atZone(zone))},
                    new String[] {"Detail rows", Integer.toString(windowed.size())},
                    new String[] {"Current invoice allowance", quota == null ? "Not available" : Long.toString(quota.invoiceLimit())},
                    new String[] {"Current allowance remaining", quota == null ? "Not available" : Long.toString(quota.remainingInvoices())},
                    new String[] {"Usage policy", "DRAFT does not consume allowance; SENT/PAID and later-cancelled issued invoices do."},
                    new String[] {"Financial policy", "SENT and PAID values are grouped by currency; DRAFT and CANCELLED values are excluded."},
                    new String[] {"Safety", "Untrusted text is exported only as formula-protected string cells."});
            header(info, List.of("Field", "Value"), styles.header()); rowNum = 1;
            for (String[] item : metadata) { Row row = info.createRow(rowNum++); text(row, 0, item[0], styles.text()); text(row, 1, item[1], styles.text()); }
            filterAndFreeze(info, rowNum, 2); widths(info, 32, 100);
            workbook.write(out); workbook.dispose(); return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Could not create the invoice report workbook.", e); }
    }

    /**
     * A flat listing of exactly the invoices a page is showing, for export. Unlike the monthly report
     * there is no grouping: the row set is whatever the caller's filters matched, in the caller's order.
     */
    public byte[] invoiceListing(List<Invoice> invoices, ZoneId zone, Map<String, String> filters, Instant generatedAt) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet("Invoices");
            List<String> headings = List.of("Invoice Number", "Status", "Issue Date", "Due Date", "Buyer", "Company Snapshot",
                    "Template Snapshot", "Currency", "Subtotal", "Tax", "Discount", "Total", "Allowance Counted", "Generated At");
            header(sheet, headings, styles.header());
            int rowNum = 1;
            for (Invoice invoice : invoices) {
                Row row = sheet.createRow(rowNum++); int c = 0;
                text(row, c++, invoice.getInvoiceNumber(), styles.text());
                text(row, c++, invoice.getStatus() == null ? "" : invoice.getStatus().name(), styles.text());
                text(row, c++, invoice.getIssueDate() == null ? "" : invoice.getIssueDate().toString(), styles.text());
                text(row, c++, invoice.getDueDate() == null ? "" : invoice.getDueDate().toString(), styles.text());
                text(row, c++, invoice.getBuyerName(), styles.text());
                text(row, c++, invoice.getCompanyNameSnapshot(), styles.text());
                text(row, c++, invoice.getTemplateName(), styles.text());
                text(row, c++, invoice.getCurrency(), styles.text());
                decimal(row, c++, invoice.getSubtotal(), styles.money());
                decimal(row, c++, invoice.getTaxAmount(), styles.money());
                decimal(row, c++, invoice.getDiscountAmount(), styles.money());
                decimal(row, c++, invoice.getTotalAmount(), styles.money());
                text(row, c++, invoice.isUsageCounted() ? "Yes" : "No", styles.text());
                text(row, c, invoice.getGeneratedAt() == null ? "" : TIME.format(invoice.getGeneratedAt().atZone(zone)), styles.text());
            }
            filterAndFreeze(sheet, rowNum, headings.size());
            widths(sheet, 22, 14, 16, 16, 28, 34, 24, 12, 16, 16, 16, 18, 20, 26);

            Sheet info = workbook.createSheet("Export Info");
            header(info, List.of("Field", "Value"), styles.header());
            int infoRow = 1;
            List<String[]> metadata = new ArrayList<>();
            metadata.add(new String[] { "Rows exported", Integer.toString(invoices.size()) });
            metadata.add(new String[] { "Generated at", TIME.format(generatedAt.atZone(zone)) });
            metadata.add(new String[] { "Timezone", zone.getId() });
            metadata.add(new String[] { "Safety", "Untrusted text is exported only as formula-protected string cells." });
            filters.forEach((key, value) -> metadata.add(new String[] { key, value == null ? "" : value }));
            for (String[] item : metadata) {
                Row row = info.createRow(infoRow++);
                text(row, 0, item[0], styles.text()); text(row, 1, item[1], styles.text());
            }
            filterAndFreeze(info, infoRow, 2); widths(info, 32, 100);
            workbook.write(out); workbook.dispose(); return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Could not create the invoice export workbook.", e); }
    }

    public byte[] audit(List<AuditRecord> records) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook); Sheet sheet = workbook.createSheet("Audit Records");
            List<String> headers = List.of("Time (UTC)", "Tenant ID", "Actor", "Action", "Outcome", "Resource Type", "Resource Name", "Resource ID", "Summary", "Before Values", "After Values");
            header(sheet, headers, styles.header()); int rowNum = 1;
            for (AuditRecord record : records) {
                Row row = sheet.createRow(rowNum++); int c = 0;
                text(row, c++, record.getCreatedAt() == null ? "" : record.getCreatedAt().toString(), styles.text());
                text(row, c++, record.getCustomer() == null ? "" : record.getCustomer().getId().toString(), styles.text());
                text(row, c++, record.getActor(), styles.text()); text(row, c++, record.getAction(), styles.text());
                text(row, c++, record.getOutcome(), styles.text()); text(row, c++, record.getResourceType(), styles.text());
                text(row, c++, record.getResourceName(), styles.text()); text(row, c++, record.getResourceId(), styles.text());
                text(row, c++, record.getSummary(), styles.text()); text(row, c++, record.getBeforeValues(), styles.text()); text(row, c, record.getAfterValues(), styles.text());
            }
            filterAndFreeze(sheet, rowNum, headers.size()); widths(sheet, 30, 14, 24, 20, 14, 20, 24, 18, 60, 48, 48);
            Sheet info = workbook.createSheet("Export Info"); header(info, List.of("Field", "Value"), styles.header());
            Row row = info.createRow(1); text(row, 0, "Records", styles.text()); number(row, 1, records.size());
            Row safe = info.createRow(2); text(safe, 0, "Safety", styles.text()); text(safe, 1, "Secrets are redacted at write time; untrusted text is formula-protected.", styles.text());
            filterAndFreeze(info, 3, 2); widths(info, 24, 80);
            workbook.write(out); workbook.dispose(); return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Could not create the audit workbook.", e); }
    }

    private BigDecimal totalFor(List<Invoice> invoices, InvoiceStatus status) {
        return invoices.stream().filter(i -> i.getStatus() == status).map(Invoice::getTotalAmount)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private void header(Sheet sheet, List<String> labels, CellStyle style) {
        Row row = sheet.createRow(0); for (int i = 0; i < labels.size(); i++) { Cell cell = row.createCell(i); cell.setCellValue(labels.get(i)); cell.setCellStyle(style); }
    }
    private void text(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index, org.apache.poi.ss.usermodel.CellType.STRING);
        cell.setCellValue(formulaSafe(value)); cell.setCellStyle(style);
    }
    private void number(Row row, int index, long value) { row.createCell(index, org.apache.poi.ss.usermodel.CellType.NUMERIC).setCellValue(value); }
    private void decimal(Row row, int index, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(index, org.apache.poi.ss.usermodel.CellType.NUMERIC);
        cell.setCellValue(value == null ? 0d : value.doubleValue()); if (style != null) cell.setCellStyle(style);
    }
    private String formulaSafe(String value) {
        if (value == null) return "";
        int i = 0; while (i < value.length() && (value.charAt(i) <= 0x20 || value.charAt(i) == '\uFEFF')) i++;
        if (i < value.length() && "=+-@".indexOf(value.charAt(i)) >= 0) return "'" + value;
        return value;
    }
    private void filterAndFreeze(Sheet sheet, int lastRowExclusive, int columns) {
        if (lastRowExclusive > 1) sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, lastRowExclusive - 1, 0, columns - 1));
        sheet.createFreezePane(0, 1);
    }
    private void widths(Sheet sheet, int... chars) { for (int i = 0; i < chars.length; i++) sheet.setColumnWidth(i, Math.min(chars[i], 100) * 256); }

    private static final class Styles {
        private final CellStyle header;
        private final CellStyle text;
        private final CellStyle money;
        Styles(SXSSFWorkbook workbook) {
            Font font = workbook.createFont(); font.setBold(true); font.setColor((short) 9);
            header = workbook.createCellStyle(); header.setFont(font); header.setWrapText(true);
            text = workbook.createCellStyle(); text.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);
            DataFormat format = workbook.createDataFormat(); money = workbook.createCellStyle(); money.setDataFormat(format.getFormat("#,##0.00;[Red]-#,##0.00"));
        }
        CellStyle header() { return header; } CellStyle text() { return text; } CellStyle money() { return money; }
    }
}
