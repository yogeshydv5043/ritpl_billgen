package com.ritpl.billgen.service;

/**
 * What a customer submits for its own copy of an invoice layout. The first four fields decide the
 * identity and the base style; the rest is presentation the account may change without ever touching
 * the system template the plan bundles.
 */
public record CustomTemplateCommand(String name, String styleKey, String accentColor, boolean active,
        String fontKey, String layoutDensity, boolean showLogo, boolean showBankDetails, String bankDetails,
        String paymentTerms, String footerText, String signatureLabel, String signatureName) {

    /** Callers that only know about the base style get the platform's presentation defaults. */
    public CustomTemplateCommand(String name, String styleKey, String accentColor, boolean active) {
        this(name, styleKey, accentColor, active, null, null, true, false, null, null, null, null, null);
    }
}
