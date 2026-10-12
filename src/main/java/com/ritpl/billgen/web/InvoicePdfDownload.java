package com.ritpl.billgen.web;

import java.time.format.DateTimeFormatter;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.ritpl.billgen.entity.Invoice;

/**
 * Shared response builder for invoice PDF downloads.
 *
 * <p>The download is always inline so the browser can show it, and the filename is derived from the
 * server-generated invoice number rather than from anything the client sent. The body is
 * uncached because a download is an audited event and the underlying record is tenant private.</p>
 */
final class InvoicePdfDownload {
    private static final MediaType PDF = MediaType.parseMediaType("application/pdf");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private InvoicePdfDownload() { }

    static ResponseEntity<byte[]> of(Invoice invoice, long id, byte[] content) {
        return ResponseEntity.ok()
                .contentType(PDF)
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(filename(invoice, id)).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(content);
    }

    private static String filename(Invoice invoice, long id) {
        String base = invoice != null && invoice.getInvoiceNumber() != null && !invoice.getInvoiceNumber().isBlank()
                ? invoice.getInvoiceNumber()
                : "invoice-" + id;
        // Only characters a filesystem and a header can safely carry ever reach the client.
        String safe = base.replaceAll("[^A-Za-z0-9._-]", "-");
        if (invoice != null && invoice.getIssueDate() != null) safe = safe + "-" + FILE_DATE.format(invoice.getIssueDate());
        return safe + ".pdf";
    }
}
