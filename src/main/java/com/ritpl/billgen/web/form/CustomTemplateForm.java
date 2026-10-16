package com.ritpl.billgen.web.form;

import com.ritpl.billgen.entity.BillTemplateStyle;
import com.ritpl.billgen.entity.CustomInvoiceTemplate;
import com.ritpl.billgen.service.CustomTemplateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CustomTemplateForm {
    @NotBlank @Size(max=100) private String name;
    @NotBlank private String styleKey = "CLASSIC";
    @Pattern(regexp="^(#[0-9a-fA-F]{6})?$", message="Use a six-digit hex color such as #2563EB") private String accentColor;
    private boolean active = true;
    // Presentation the account chooses for its own copy; re-checked by the service.
    private String fontKey = "DEFAULT";
    private String layoutDensity = "COMFORTABLE";
    private boolean showLogo = true;
    private boolean showBankDetails = false;
    @Size(max=600) private String bankDetails;
    @Size(max=240) private String paymentTerms;
    @Size(max=600) private String footerText;
    @Size(max=120) private String signatureLabel;
    @Size(max=120) private String signatureName;
    public static CustomTemplateForm from(CustomInvoiceTemplate template) {
        CustomTemplateForm f = new CustomTemplateForm(); f.name = template.getName(); f.styleKey = template.getStyleKey();
        f.accentColor = template.getAccentColor(); f.active = template.isActive();
        f.fontKey = template.getFontKey(); f.layoutDensity = template.getLayoutDensity();
        f.showLogo = template.isShowLogo(); f.showBankDetails = template.isShowBankDetails();
        f.bankDetails = template.getBankDetails(); f.paymentTerms = template.getPaymentTerms();
        f.footerText = template.getFooterText(); f.signatureLabel = template.getSignatureLabel();
        f.signatureName = template.getSignatureName();
        return f;
    }
    public CustomTemplateCommand toCommand() {
        return new CustomTemplateCommand(name, styleKey, accentColor, active, fontKey, layoutDensity, showLogo,
                showBankDetails, bankDetails, paymentTerms, footerText, signatureLabel, signatureName);
    }
    public String getFontKey(){return fontKey;} public void setFontKey(String v){fontKey=v;}
    public String getLayoutDensity(){return layoutDensity;} public void setLayoutDensity(String v){layoutDensity=v;}
    public boolean isShowLogo(){return showLogo;} public void setShowLogo(boolean v){showLogo=v;}
    public boolean isShowBankDetails(){return showBankDetails;} public void setShowBankDetails(boolean v){showBankDetails=v;}
    public String getBankDetails(){return bankDetails;} public void setBankDetails(String v){bankDetails=v;}
    public String getPaymentTerms(){return paymentTerms;} public void setPaymentTerms(String v){paymentTerms=v;}
    public String getFooterText(){return footerText;} public void setFooterText(String v){footerText=v;}
    public String getSignatureLabel(){return signatureLabel;} public void setSignatureLabel(String v){signatureLabel=v;}
    public String getSignatureName(){return signatureName;} public void setSignatureName(String v){signatureName=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getStyleKey(){return styleKey;} public void setStyleKey(String v){styleKey=v;}
    public String getAccentColor(){return accentColor;} public void setAccentColor(String v){accentColor=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
