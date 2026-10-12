package com.ritpl.billgen.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceItem;

/**
 * Renders a saved invoice as a PDF.
 *
 * <p>Every value printed here comes from the invoice's own snapshot columns and line items, so a
 * download keeps showing the company details, prices, taxes, totals and template accent that were in
 * force when the invoice was issued - even after the profile, catalogue or template is edited.</p>
 */
@Service
public class InvoicePdfService {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Color RULE = new Color(228, 232, 238);
    private static final Color HIGHLIGHT = new Color(245, 247, 250);

    public byte[] render(Invoice invoice) {
        Color accent = accentColor(invoice.getTemplateAccentColor());
        Document document = new Document(PageSize.A4, 42, 42, 46, 42);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(header(invoice, accent));
            document.add(divider(accent));
            document.add(spacer(12));
            document.add(items(invoice));
            document.add(spacer(12));
            document.add(totals(invoice, accent));
            if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
                document.add(spacer(14));
                document.add(note(invoice.getNotes()));
            }
            document.add(spacer(18));
            document.add(footer(invoice));
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            if (document.isOpen()) document.close();
            throw new IllegalStateException("Could not create the invoice PDF.", e);
        }
    }

    private PdfPTable header(Invoice invoice, Color accent) {
        PdfPTable table = new PdfPTable(new float[] { 1.15f, 1f });
        table.setWidthPercentage(100);

        PdfPCell issuer = openCell(Element.ALIGN_LEFT);
        issuer.addElement(paragraph("ISSUED BY", LABEL));
        issuer.addElement(paragraph(text(invoice.getCompanyNameSnapshot(), "Invoice"), HEADING));
        issuer.addElement(paragraph(issuerDetails(invoice), SMALL));
        if (invoice.getCompanyGstinSnapshot() != null) {
            issuer.addElement(paragraph("GSTIN " + invoice.getCompanyGstinSnapshot(), SMALL));
        }
        Image logo = logo(invoice);
        if (logo != null) issuer.addElement(logo);

        PdfPCell title = openCell(Element.ALIGN_RIGHT);
        title.addElement(paragraph("TAX INVOICE", LABEL));
        title.addElement(invoiceNumber(text(invoice.getInvoiceNumber(), "-"), accent));
        title.addElement(paragraph(invoice.getStatus().name() + "   ·   Template: "
                + text(invoice.getTemplateName(), "default"), SMALL));
        title.addElement(paragraph("Issued " + invoice.getIssueDate() + "   ·   Due " + invoice.getDueDate(), SMALL));

        table.addCell(issuer);
        table.addCell(title);
        return table;
    }

    private PdfPTable items(Invoice invoice) {
        PdfPTable table = new PdfPTable(new float[] { 4.2f, 1f, 1.4f, 1.1f, 1.5f });
        table.setWidthPercentage(100);
        addHeading(table, "DESCRIPTION", Element.ALIGN_LEFT);
        addHeading(table, "QTY", Element.ALIGN_RIGHT);
        addHeading(table, "UNIT PRICE", Element.ALIGN_RIGHT);
        addHeading(table, "TAX", Element.ALIGN_RIGHT);
        addHeading(table, "LINE TOTAL", Element.ALIGN_RIGHT);

        List<InvoiceItem> lines = invoice.getItems();
        if (lines.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No line items recorded.", SMALL));
            empty.setColspan(5);
            empty.setPadding(6f);
            empty.setBorder(Rectangle.BOTTOM);
            table.addCell(empty);
        }
        for (InvoiceItem line : lines) {
            addBody(table, text(line.getDescription(), "-"), BODY, Element.ALIGN_LEFT);
            addBody(table, plain(line.getQuantity()), BODY, Element.ALIGN_RIGHT);
            addBody(table, money(invoice, line.getUnitPrice()), BODY, Element.ALIGN_RIGHT);
            addBody(table, plain(line.getTaxRate()) + "%", BODY, Element.ALIGN_RIGHT);
            addBody(table, money(invoice, line.getLineTotal()), BODY_BOLD, Element.ALIGN_RIGHT);
        }
        return table;
    }

    private PdfPTable totals(Invoice invoice, Color accent) {
        PdfPTable table = new PdfPTable(new float[] { 1.4f, 1f });
        table.setWidthPercentage(52f);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalRow(table, "Subtotal", money(invoice, invoice.getSubtotal()), false);
        totalRow(table, "Tax", money(invoice, invoice.getTaxAmount()), false);
        if (invoice.getDiscountAmount() != null && invoice.getDiscountAmount().signum() > 0) {
            totalRow(table, "Discount", "− " + money(invoice, invoice.getDiscountAmount()), false);
        }
        totalRow(table, "Total", money(invoice, invoice.getTotalAmount()), true);
        return table;
    }

    private void totalRow(PdfPTable table, String label, String value, boolean emphasise) {
        Font font = emphasise ? BODY_BOLD : BODY;
        PdfPCell left = new PdfPCell(new Phrase(label, font));
        left.setBorder(Rectangle.TOP + Rectangle.BOTTOM);
        left.setBorderColor(RULE);
        left.setPadding(5f);
        PdfPCell right = new PdfPCell(new Phrase(value, font));
        right.setBorder(Rectangle.TOP + Rectangle.BOTTOM);
        right.setBorderColor(RULE);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setPadding(5f);
        if (emphasise) right.setBackgroundColor(HIGHLIGHT);
        table.addCell(left);
        table.addCell(right);
    }

    private void addHeading(PdfPTable table, String label, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(label, LABEL));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(6f);
        cell.setBorder(Rectangle.BOTTOM);
        table.addCell(cell);
    }

    private void addBody(PdfPTable table, String value, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5f);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(RULE);
        table.addCell(cell);
    }

    private PdfPTable divider(Color accent) {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell line = new PdfPCell(new Phrase(" ", SMALL));
        line.setBorder(Rectangle.BOTTOM);
        line.setBorderColor(accent);
        line.setBorderWidthBottom(1.2f);
        line.setPadding(0f);
        rule.addCell(line);
        return rule;
    }

    private PdfPTable note(String value) {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase("A NOTE   " + value.trim(), SMALL));
        cell.setBorder(Rectangle.LEFT);
        cell.setPadding(6f);
        cell.setPaddingLeft(10f);
        box.addCell(cell);
        return box;
    }

    private Paragraph footer(Invoice invoice) {
        return paragraph("Values shown are the snapshot recorded when "
                + text(invoice.getInvoiceNumber(), "this invoice")
                + " was issued; later profile, catalogue or template changes do not alter it.", LABEL);
    }

    private PdfPCell openCell(int alignment) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);
        cell.setPaddingTop(2f);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private String issuerDetails(Invoice invoice) {
        StringBuilder details = new StringBuilder();
        append(details, invoice.getCompanyContactSnapshot());
        append(details, invoice.getCompanyEmailSnapshot());
        append(details, invoice.getCompanyPhoneSnapshot());
        append(details, invoice.getCompanyAddressSnapshot());
        return details.isEmpty() ? " " : details.toString();
    }

    /** A logo that cannot be decoded must never stop the financial document from rendering. */
    private Image logo(Invoice invoice) {
        byte[] bytes = invoice.getCompanyLogoSnapshot();
        String contentType = invoice.getCompanyLogoContentTypeSnapshot();
        if (bytes == null || bytes.length == 0
                || (contentType != null && !contentType.equalsIgnoreCase("image/png") && !contentType.equalsIgnoreCase("image/jpeg"))) {
            return null;
        }
        try {
            Image image = Image.getInstance(bytes);
            image.scaleToFit(120f, 52f);
            return image;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Paragraph paragraph(String value, Font font) {
        Paragraph paragraph = new Paragraph(value, font);
        paragraph.setLeading(font.getSize() + 3.4f);
        return paragraph;
    }

    /** The stored template accent tints the invoice number; anything unparsable falls back to ink. */
    private Paragraph invoiceNumber(String value, Color accent) {
        Paragraph paragraph = new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Font.BOLD, accent));
        paragraph.setLeading(24f);
        return paragraph;
    }

    private Paragraph spacer(float height) {
        Paragraph paragraph = new Paragraph(" ");
        paragraph.setLeading(height);
        return paragraph;
    }

    private String money(Invoice invoice, BigDecimal value) {
        return invoice.getCurrency() + " " + MONEY.format(value == null ? BigDecimal.ZERO : value);
    }

    private String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String text(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    private void append(StringBuilder target, String value) {
        if (value == null || value.isBlank()) return;
        if (!target.isEmpty()) target.append("   ·   ");
        target.append(value.trim());
    }

    /** Only a literal {@code #rrggbb} value from the stored snapshot is ever used as a colour. */
    private Color accentColor(String value) {
        if (value != null && value.matches("#[0-9a-fA-F]{6}")) {
            return new Color(Integer.parseInt(value.substring(1), 16));
        }
        return new Color(33, 37, 41);
    }
}
