package com.ritpl.billgen.web.form;

import java.math.BigDecimal;
import com.ritpl.billgen.entity.AuditHistoryPeriod;
import com.ritpl.billgen.entity.CustomerSettings;
import com.ritpl.billgen.service.CustomerSettingsCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The settings screen's backing object. Field-level rules give the customer an error next to the box
 * they got wrong; the service still validates everything again, because a preference is only useful
 * while it is also enforceable.
 */
public class CustomerSettingsForm {
    private Long defaultTemplateId;
    private Long defaultCustomTemplateId;
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Use a three-letter currency code such as INR.")
    @Size(min = 3, max = 3) private String defaultCurrency = "INR";
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9-]{0,7}$", message = "Start with a letter; letters, digits and hyphens only, up to 8 characters.")
    @Size(max = 8) private String invoiceNumberPrefix = "INV-";
    @Min(0) @Max(365) private Integer defaultDueDays = 14;
    @DecimalMin("0.00") @DecimalMax("100.00") private BigDecimal defaultTaxRate;
    @Size(max = 240) private String defaultPaymentTerms;
    @Size(max = 600) private String defaultNotes;
    @Size(max = 600) private String defaultFooter;
    @Size(max = 24) private String defaultUnit;
    private Long defaultCategoryId;
    private Integer listPageSize = 20;
    private AuditHistoryPeriod auditHistoryPeriod = AuditHistoryPeriod.ONE_YEAR;
    private boolean notifyExpiryReminders = true;
    private boolean notifyQuotaAlerts = true;
    private boolean notifyPaymentUpdates = true;
    private boolean notifySecurityAlerts = true;
    @Min(7) @Max(730) private Integer exportRangeDays = 90;

    public static CustomerSettingsForm from(CustomerSettings settings) {
        CustomerSettingsForm form = new CustomerSettingsForm();
        if (settings == null) return form;
        form.defaultTemplateId = settings.getDefaultTemplateId();
        form.defaultCustomTemplateId = settings.getDefaultCustomTemplateId();
        form.defaultCurrency = settings.getDefaultCurrency();
        form.invoiceNumberPrefix = settings.getInvoiceNumberPrefix();
        form.defaultDueDays = settings.getDefaultDueDays();
        form.defaultTaxRate = settings.getDefaultTaxRate();
        form.defaultPaymentTerms = settings.getDefaultPaymentTerms();
        form.defaultNotes = settings.getDefaultNotes();
        form.defaultFooter = settings.getDefaultFooter();
        form.defaultUnit = settings.getDefaultUnit();
        form.defaultCategoryId = settings.getDefaultCategoryId();
        form.listPageSize = settings.getListPageSize();
        form.auditHistoryPeriod = settings.getAuditHistoryPeriod();
        form.notifyExpiryReminders = settings.isNotifyExpiryReminders();
        form.notifyQuotaAlerts = settings.isNotifyQuotaAlerts();
        form.notifyPaymentUpdates = settings.isNotifyPaymentUpdates();
        form.notifySecurityAlerts = settings.isNotifySecurityAlerts();
        form.exportRangeDays = settings.getExportRangeDays();
        return form;
    }

    public CustomerSettingsCommand toCommand() {
        return new CustomerSettingsCommand(defaultTemplateId, defaultCustomTemplateId, defaultCurrency,
                invoiceNumberPrefix, defaultDueDays, defaultTaxRate, defaultPaymentTerms, defaultNotes, defaultFooter,
                defaultUnit, defaultCategoryId, listPageSize, auditHistoryPeriod, notifyExpiryReminders,
                notifyQuotaAlerts, notifyPaymentUpdates, notifySecurityAlerts, exportRangeDays);
    }

    public Long getDefaultTemplateId(){return defaultTemplateId;} public void setDefaultTemplateId(String v){defaultTemplateId = parse(v);}
    public Long getDefaultCustomTemplateId(){return defaultCustomTemplateId;} public void setDefaultCustomTemplateId(String v){defaultCustomTemplateId = parse(v);}
    public Long getDefaultCategoryId(){return defaultCategoryId;} public void setDefaultCategoryId(String v){defaultCategoryId = parse(v);}
    private static Long parse(String value) { return value == null || value.isBlank() ? null : Long.valueOf(value.trim()); }

    public String getDefaultCurrency(){return defaultCurrency;} public void setDefaultCurrency(String v){defaultCurrency=v;}
    public String getInvoiceNumberPrefix(){return invoiceNumberPrefix;} public void setInvoiceNumberPrefix(String v){invoiceNumberPrefix=v;}
    public Integer getDefaultDueDays(){return defaultDueDays;} public void setDefaultDueDays(Integer v){defaultDueDays=v;}
    public BigDecimal getDefaultTaxRate(){return defaultTaxRate;} public void setDefaultTaxRate(BigDecimal v){defaultTaxRate=v;}
    public String getDefaultPaymentTerms(){return defaultPaymentTerms;} public void setDefaultPaymentTerms(String v){defaultPaymentTerms=v;}
    public String getDefaultNotes(){return defaultNotes;} public void setDefaultNotes(String v){defaultNotes=v;}
    public String getDefaultFooter(){return defaultFooter;} public void setDefaultFooter(String v){defaultFooter=v;}
    public String getDefaultUnit(){return defaultUnit;} public void setDefaultUnit(String v){defaultUnit=v;}
    public Integer getListPageSize(){return listPageSize;} public void setListPageSize(Integer v){listPageSize=v;}
    public AuditHistoryPeriod getAuditHistoryPeriod(){return auditHistoryPeriod;} public void setAuditHistoryPeriod(AuditHistoryPeriod v){auditHistoryPeriod=v;}
    public boolean isNotifyExpiryReminders(){return notifyExpiryReminders;} public void setNotifyExpiryReminders(boolean v){notifyExpiryReminders=v;}
    public boolean isNotifyQuotaAlerts(){return notifyQuotaAlerts;} public void setNotifyQuotaAlerts(boolean v){notifyQuotaAlerts=v;}
    public boolean isNotifyPaymentUpdates(){return notifyPaymentUpdates;} public void setNotifyPaymentUpdates(boolean v){notifyPaymentUpdates=v;}
    public boolean isNotifySecurityAlerts(){return notifySecurityAlerts;} public void setNotifySecurityAlerts(boolean v){notifySecurityAlerts=v;}
    public Integer getExportRangeDays(){return exportRangeDays;} public void setExportRangeDays(Integer v){exportRangeDays=v;}
}
