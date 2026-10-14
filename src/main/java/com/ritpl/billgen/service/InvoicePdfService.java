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
 * force when the invoice was issued - even after the profile, catalogue or template is edited. The
 * typeface, spacing, payment terms, remittance block, footer line and signature line an account had
 * chosen for its template are snapshot the same way, which is what lets the PDF match the print view
 * of the very same document without reading anything that may have changed since.</p>
 */
@Service
public class InvoicePdfService {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final Color RULE = new Color(228, 232, 238);
    private static final Color HIGHLIGHT = new Color(245, 247, 250);

    public byte[] render(Invoice invoice) {
        Color accent = accentColor(invoice.getTemplateAccentColor());
        Fonts fonts = Fonts.of(invoice);
        Document document = new Document(PageSize.A4, 42, 42, 46, 42);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(header(invoice, accent, fonts));
            document.add(divider(accent, fonts));
            document.add(spacer(fonts.gap()));
            document.add(items(invoice, fonts));
            document.add(spacer(fonts.gap()));
            document.add(totals(invoice, fonts));
            if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
                document.add(spacer(fonts.gap() + 2));
                document.add(note("A NOTE", invoice.getNotes(), fonts));
            }
            if (notBlank(invoice.getPaymentTerms())) {
                document.add(spacer(fonts.gap()));
                document.add(note("PAYMENT TERMS", invoice.getPaymentTerms(), fonts));
            }
            if (notBlank(invoice.getBankDetails())) {
                document.add(spacer(fonts.gap()));
                document.add(note("REMITTANCE DETAILS", invoice.getBankDetails(), fonts));
            }
            if (invoice.hasSignatureBlock()) {
                document.add(spacer(fonts.gap() + 10));
                document.add(paragraph("____________________________", fonts.small()));
                document.add(paragraph(signatureCaption(invoice), fonts.small()));
            }
            document.add(spacer(fonts.gap() + 6));
            document.add(footer(invoice, fonts));
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            if (document.isOpen()) document.close();
            throw new IllegalStateException("Could not create the invoice PDF.", e);
        }
    }

    private PdfPTable header(Invoice invoice, Color accent, Fonts fonts) {
        PdfPTable table = new PdfPTable(new float[] { 1.15f, 1f });
        table.setWidthPercentage(100);

        PdfPCell issuer = openCell(Element.ALIGN_LEFT);
        issuer.addElement(paragraph("ISSUED BY", fonts.label()));
        issuer.addElement(paragraph(text(invoice.getCompanyNameSnapshot(), "Invoice"), fonts.heading()));
        issuer.addElement(paragraph(issuerDetails(invoice), fonts.small()));
        if (invoice.getCompanyGstinSnapshot() != null) {
            issuer.addElement(paragraph("GSTIN " + invoice.getCompanyGstinSnapshot(), fonts.small()));
        }
        // Whether the mark appears at all is the account's own layout choice, taken at issue time.
        Image logo = invoice.printsLogo() ? logo(invoice) : null;
        if (logo != null) issuer.addElement(logo);

        PdfPCell title = openCell(Element.ALIGN_RIGHT);
        title.addElement(paragraph("TAX INVOICE", fonts.label()));
        title.addElement(invoiceNumber(text(invoice.getInvoiceNumber(), "-"), accent, fonts));
        title.addElement(paragraph(invoice.getStatus().name() + "   ·   Template: "
                + text(invoice.getTemplateName(), "default"), fonts.small()));
        title.addElement(paragraph("Issued " + invoice.getIssueDate() + "   ·   Due " + invoice.getDueDate(), fonts.small()));

        table.addCell(issuer);
        table.addCell(title);
        return table;
    }

    private PdfPTable items(Invoice invoice, Fonts fonts) {
        PdfPTable table = new PdfPTable(new float[] { 4.2f, 1f, 1.4f, 1.1f, 1.5f });
        table.setWidthPercentage(100);
        addHeading(table, "DESCRIPTION", Element.ALIGN_LEFT, fonts);
        addHeading(table, "QTY", Element.ALIGN_RIGHT, fonts);
        addHeading(table, "UNIT PRICE", Element.ALIGN_RIGHT, fonts);
        addHeading(table, "TAX", Element.ALIGN_RIGHT, fonts);
        addHeading(table, "LINE TOTAL", Element.ALIGN_RIGHT, fonts);

        List<InvoiceItem> lines = invoice.getItems();
        if (lines.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No line items recorded.", fonts.small()));
            empty.setColspan(5);
            empty.setPadding(6f);
            empty.setBorder(Rectangle.BOTTOM);
            table.addCell(empty);
        }
        for (InvoiceItem line : lines) {
            addBody(table, text(line.getDescription(), "-"), fonts.body(), Element.ALIGN_LEFT, fonts);
            addBody(table, plain(line.getQuantity()), fonts.body(), Element.ALIGN_RIGHT, fonts);
            addBody(table, money(invoice, line.getUnitPrice()), fonts.body(), Element.ALIGN_RIGHT, fonts);
            addBody(table, plain(line.getTaxRate()) + "%", fonts.body(), Element.ALIGN_RIGHT, fonts);
            addBody(table, money(invoice, line.getLineTotal()), fonts.bodyBold(), Element.ALIGN_RIGHT, fonts);
        }
        return table;
    }

    private PdfPTable totals(Invoice invoice, Fonts fonts) {
        PdfPTable table = new PdfPTable(new float[] { 1.4f, 1f });
        table.setWidthPercentage(52f);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalRow(table, "Subtotal", money(invoice, invoice.getSubtotal()), false, fonts);
        totalRow(table, "Tax", money(invoice, invoice.getTaxAmount()), false, fonts);
        if (invoice.getDiscountAmount() != null && invoice.getDiscountAmount().signum() > 0) {
            totalRow(table, "Discount", "− " + money(invoice, invoice.getDiscountAmount()), false, fonts);
        }
        totalRow(table, "Total", money(invoice, invoice.getTotalAmount()), true, fonts);
        return table;
    }

    private void totalRow(PdfPTable table, String label, String value, boolean emphasise, Fonts fonts) {
        Font font = emphasise ? fonts.bodyBold() : fonts.body();
        float padding = fonts.compact() ? 3.5f : 5f;
        PdfPCell left = new PdfPCell(new Phrase(label, font));
        left.setBorder(Rectangle.TOP + Rectangle.BOTTOM);
        left.setBorderColor(RULE);
        left.setPadding(padding);
        PdfPCell right = new PdfPCell(new Phrase(value, font));
        right.setBorder(Rectangle.TOP + Rectangle.BOTTOM);
        right.setBorderColor(RULE);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setPadding(padding);
        if (emphasise) right.setBackgroundColor(HIGHLIGHT);
        table.addCell(left);
        table.addCell(right);
    }

    private void addHeading(PdfPTable table, String label, int alignment, Fonts fonts) {
        PdfPCell cell = new PdfPCell(new Phrase(label, fonts.label()));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(fonts.compact() ? 4f : 6f);
        cell.setBorder(Rectangle.BOTTOM);
        table.addCell(cell);
    }

    private void addBody(PdfPTable table, String value, Font font, int alignment, Fonts fonts) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(fonts.compact() ? 3.5f : 5f);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(RULE);
        table.addCell(cell);
    }

    private PdfPTable divider(Color accent, Fonts fonts) {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell line = new PdfPCell(new Phrase(" ", fonts.small()));
        line.setBorder(Rectangle.BOTTOM);
        line.setBorderColor(accent);
        line.setBorderWidthBottom(1.2f);
        line.setPadding(0f);
        rule.addCell(line);
        return rule;
    }

    /** A left-ruled block of text: the note, the account's payment terms and its remittance details. */
    private PdfPTable note(String label, String value, Fonts fonts) {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase(label + "   " + value.trim(), fonts.small()));
        cell.setBorder(Rectangle.LEFT);
        cell.setPadding(6f);
        cell.setPaddingLeft(10f);
        box.addCell(cell);
        return box;
    }

    private Paragraph footer(Invoice invoice, Fonts fonts) {
        String chosen = invoice.getFooterText();
        if (chosen != null && !chosen.isBlank()) return paragraph(chosen, fonts.label());
        return paragraph("Values shown are the snapshot recorded when "
                + text(invoice.getInvoiceNumber(), "this invoice")
                + " was issued; later profile, catalogue or template changes do not alter it.", fonts.label());
    }

    /** The caption under the signature rule: the account's own label, and its name when it gave one. */
    private String signatureCaption(Invoice invoice) {
        String label = text(invoice.getSignatureLabel(), "Authorised signatory");
        String name = invoice.getSignatureName();
        return notBlank(name) ? label + "  ·  " + name : label;
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
    private Paragraph invoiceNumber(String value, Color accent, Fonts fonts) {
        Paragraph paragraph = new Paragraph(value, FontFactory.getFont(fonts.boldFamily(), 20 * fonts.scale(), Font.BOLD, accent));
        paragraph.setLeading(fonts.titleLeading());
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

    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }

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

    /**
     * The typeface set for a single document. The service is a shared singleton, so the fonts are built
     * per render and passed down rather than stored on the bean: two invoices being rendered at the
     * same time can then use different families without interfering with each other.
     */
    private record Fonts(String plainFamily, String boldFamily, float scale, boolean compact,
            Font heading, Font label, Font body, Font bodyBold, Font small) {

        static Fonts of(Invoice invoice) {
            String family = invoice.getPresentationFont();
            boolean compact = invoice.isCompactPresentation();
            String plain;
            String bold;
            switch (family == null ? "DEFAULT" : family) {
                case "SERIF" -> { plain = FontFactory.TIMES_ROMAN; bold = FontFactory.TIMES_BOLD; }
                case "MONO" -> { plain = FontFactory.COURIER; bold = FontFactory.COURIER_BOLD; }
                default -> { plain = FontFactory.HELVETICA; bold = FontFactory.HELVETICA_BOLD; }
            }
            float scale = compact ? 0.9f : 1f;
            return new Fonts(plain, bold, scale, compact,
                    FontFactory.getFont(bold, 11 * scale),
                    FontFactory.getFont(plain, 8 * scale, Font.NORMAL, Color.GRAY),
                    FontFactory.getFont(plain, 10 * scale),
                    FontFactory.getFont(bold, 10 * scale),
                    FontFactory.getFont(plain, 9 * scale));
        }

        float gap() { return compact ? 8f : 12f; }

        float titleLeading() { return 24f * scale; }
    }
}
