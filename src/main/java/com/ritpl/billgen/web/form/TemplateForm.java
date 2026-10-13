package com.ritpl.billgen.web.form;

import com.ritpl.billgen.entity.BillTemplate;
import com.ritpl.billgen.entity.BillTemplateStyle;
import com.ritpl.billgen.service.TemplateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TemplateForm {
    @NotBlank @Size(max=40) @Pattern(regexp="[A-Za-z0-9_-]+") private String code;
    @NotBlank @Size(max=100) private String name;
    @Size(max=500) private String description;
    @NotNull private BillTemplateStyle style = BillTemplateStyle.CLASSIC;
    @Pattern(regexp="^(#[0-9a-fA-F]{6})?$", message="Use a hex colour such as #21826A") private String accentColor;
    @Size(max = 500) private String headerText;
    @Size(max = 500) private String footerText;
    @Size(max = 500) private String paymentTerms;
    @Size(max = 2000) private String termsAndConditions;
    private boolean active = true;
    private int sortOrder = 10;

    public static TemplateForm from(BillTemplate template) {
        TemplateForm form = new TemplateForm();
        form.code = template.getCode();
        form.name = template.getName();
        form.description = template.getDescription();
        form.style = template.getStyle();
        form.accentColor = template.getAccentColor();
        form.headerText = template.getHeaderText();
        form.footerText = template.getFooterText();
        form.paymentTerms = template.getPaymentTerms();
        form.termsAndConditions = template.getTermsAndConditions();
        form.active = template.isActive();
        form.sortOrder = template.getSortOrder();
        return form;
    }

    public TemplateCommand toCommand() {
        return new TemplateCommand(code, name, description, style, accentColor, active, sortOrder,
                headerText, footerText, paymentTerms, termsAndConditions);
    }

    public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public BillTemplateStyle getStyle(){return style;} public void setStyle(BillTemplateStyle v){style=v;}
    public String getAccentColor(){return accentColor;} public void setAccentColor(String v){accentColor=v;}
    public String getHeaderText(){return headerText;} public void setHeaderText(String v){headerText=v;}
    public String getFooterText(){return footerText;} public void setFooterText(String v){footerText=v;}
    public String getPaymentTerms(){return paymentTerms;} public void setPaymentTerms(String v){paymentTerms=v;}
    public String getTermsAndConditions(){return termsAndConditions;} public void setTermsAndConditions(String v){termsAndConditions=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public int getSortOrder(){return sortOrder;} public void setSortOrder(int v){sortOrder=v;}
}