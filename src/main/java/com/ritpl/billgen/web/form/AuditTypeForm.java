package com.ritpl.billgen.web.form;

import com.ritpl.billgen.entity.AuditEventCategory;
import com.ritpl.billgen.entity.AuditType;
import com.ritpl.billgen.entity.LimitPeriod;
import com.ritpl.billgen.service.AuditTypeCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What the application tracks, the quota that applies to it and how long it is kept. */
public class AuditTypeForm {
    @NotBlank @Size(min = 3, max = 48) @Pattern(regexp = "[A-Za-z0-9_-]+") private String code;
    @NotBlank @Size(max = 120) private String name;
    @Size(max = 500) private String description;
    @NotNull private AuditEventCategory eventCategory = AuditEventCategory.SYSTEM;
    @Size(max = 48) private String action;
    @Size(max = 48) private String resourceType;
    @NotNull private LimitPeriod limitPeriod = LimitPeriod.NONE;
    @Min(1) private Integer usageLimit;
    @Min(1) @Max(365) private Integer customPeriodDays;
    @Min(1) @Max(36500) private int retentionDays = 730;
    @Min(1) @Max(100) private int alertThresholdPercent = 80;
    private boolean active = true;

    public static AuditTypeForm from(AuditType type) {
        AuditTypeForm form = new AuditTypeForm();
        form.code = type.getCode(); form.name = type.getName(); form.description = type.getDescription();
        form.eventCategory = type.getEventCategory(); form.action = type.getAction(); form.resourceType = type.getResourceType();
        form.limitPeriod = type.getLimitPeriod(); form.usageLimit = type.getUsageLimit();
        form.customPeriodDays = type.getCustomPeriodDays(); form.retentionDays = type.getRetentionDays();
        form.alertThresholdPercent = type.getAlertThresholdPercent(); form.active = type.isActive();
        return form;
    }

    public AuditTypeCommand toCommand() {
        return new AuditTypeCommand(code, name, description, eventCategory, action, resourceType, limitPeriod,
                usageLimit, customPeriodDays, retentionDays, alertThresholdPercent, active);
    }

    public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public AuditEventCategory getEventCategory(){return eventCategory;} public void setEventCategory(AuditEventCategory v){eventCategory=v;}
    public String getAction(){return action;} public void setAction(String v){action=v;}
    public String getResourceType(){return resourceType;} public void setResourceType(String v){resourceType=v;}
    public LimitPeriod getLimitPeriod(){return limitPeriod;} public void setLimitPeriod(LimitPeriod v){limitPeriod=v;}
    public Integer getUsageLimit(){return usageLimit;} public void setUsageLimit(Integer v){usageLimit=v;}
    public Integer getCustomPeriodDays(){return customPeriodDays;} public void setCustomPeriodDays(Integer v){customPeriodDays=v;}
    public int getRetentionDays(){return retentionDays;} public void setRetentionDays(int v){retentionDays=v;}
    public int getAlertThresholdPercent(){return alertThresholdPercent;} public void setAlertThresholdPercent(int v){alertThresholdPercent=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
