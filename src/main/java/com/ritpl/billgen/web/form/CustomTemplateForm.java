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
    public static CustomTemplateForm from(CustomInvoiceTemplate template) {
        CustomTemplateForm f = new CustomTemplateForm(); f.name = template.getName(); f.styleKey = template.getStyleKey();
        f.accentColor = template.getAccentColor(); f.active = template.isActive(); return f;
    }
    public CustomTemplateCommand toCommand() { return new CustomTemplateCommand(name, styleKey, accentColor, active); }
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getStyleKey(){return styleKey;} public void setStyleKey(String v){styleKey=v;}
    public String getAccentColor(){return accentColor;} public void setAccentColor(String v){accentColor=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
