package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import com.ritpl.billgen.entity.Invoice;
import com.ritpl.billgen.entity.InvoiceItem;
import com.ritpl.billgen.entity.InvoiceStatus;

/** The invoice PDF must carry the saved values, not anything that changed afterwards. */
class InvoicePdfServiceTest {

    private final InvoicePdfService renderer = new InvoicePdfService();

    @Test
    void rendersAValidSinglePageDocumentFromTheStoredSnapshot() throws IOException {
        byte[] pdf = renderer.render(invoice());

        assertTrue(pdf.length > 1_000, "a real document is produced, not a stub");
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
        try (PdfReader reader = new PdfReader(pdf)) {
            assertEquals(1, reader.getNumberOfPages());
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(text.contains("INV-000142"), "the invoice number is printed");
            assertTrue(text.contains("Acme Traders"), "the company snapshot is printed");
            assertTrue(text.contains("GSTIN 22AAAAA0000A1Z5"), "the tax registration is printed");
            assertTrue(text.contains("1,162.00"), "the stored grand total is printed");
            assertTrue(text.contains("1,000.00") && text.contains("180.00"), "line subtotals and tax are printed");
            assertTrue(text.contains("Design workshop"), "the line description snapshot is printed");
            assertTrue(text.contains("Thank you"), "the invoice note is printed");
        }
    }

    @Test
    void laterProfileAndCatalogueChangesAreNotReflectedInTheDocument() throws IOException {
        Invoice invoice = invoice();
        // The live tenant record says something else; the PDF must still show the snapshot.
        invoice.getCustomer().setCompanyName("Long Renamed Holdings Limited");

        try (PdfReader reader = new PdfReader(renderer.render(invoice))) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertFalse(text.contains("Long Renamed Holdings"), "the renamed profile never reaches the document");
            assertTrue(text.contains("Acme Traders"), "the name in force at issue time is preserved");
        }
    }

    @Test
    void unsafeOrUndecodableTemplateDataIsIgnoredRatherThanRendered() throws IOException {
        Invoice invoice = invoice();
        invoice.setTemplateAccentSnapshot("javascript:alert(1)");
        invoice.setCompanyLogoSnapshot("not really a png".getBytes(StandardCharsets.UTF_8));
        invoice.setCompanyLogoContentTypeSnapshot("image/png");

        byte[] pdf = renderer.render(invoice);

        try (PdfReader reader = new PdfReader(pdf)) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertFalse(text.contains("javascript"), "untrusted template text is never emitted as a script");
            assertTrue(text.contains("INV-000142"), "a broken logo does not stop the financial document");
        }
    }

    @Test
    void anInvoiceWithoutLinesStillProducesAReadableDocument() throws IOException {
        Invoice invoice = invoice();
        invoice.getItems().clear();

        try (PdfReader reader = new PdfReader(renderer.render(invoice))) {
            assertTrue(new PdfTextExtractor(reader).getTextFromPage(1).contains("No line items recorded."));
        }
    }

    private Invoice invoice() {
        Invoice invoice = new Invoice();
        var customer = new com.ritpl.billgen.entity.Customer("Acme Traders", "Owner", "billing@acme.test");
        invoice.setCustomer(customer);
        invoice.setInvoiceNumber("INV-000142");
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setCurrency("INR");
        invoice.setIssueDate(LocalDate.of(2026, 10, 1));
        invoice.setDueDate(LocalDate.of(2026, 10, 15));
        invoice.setSubtotal(new BigDecimal("1000.00"));
        invoice.setTaxAmount(new BigDecimal("180.00"));
        invoice.setDiscountAmount(new BigDecimal("18.00"));
        invoice.setTotalAmount(new BigDecimal("1162.00"));
        invoice.setCompanyNameSnapshot("Acme Traders");
        invoice.setCompanyContactSnapshot("Owner");
        invoice.setCompanyEmailSnapshot("billing@acme.test");
        invoice.setCompanyAddressSnapshot("12 Market Road, Ludhiana");
        invoice.setCompanyGstinSnapshot("22AAAAA0000A1Z5");
        invoice.setTemplateNameSnapshot("Classic");
        invoice.setTemplateAccentSnapshot("#0F766E");
        invoice.setNotes("Thank you for your business.");
        invoice.addItem(new InvoiceItem("Design workshop", new BigDecimal("4"), new BigDecimal("250.00"),
                new BigDecimal("18.00"), new BigDecimal("1000.00"), new BigDecimal("180.00"),
                new BigDecimal("1180.00")));
        return invoice;
    }
}
