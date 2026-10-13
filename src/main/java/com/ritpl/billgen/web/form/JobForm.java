package com.ritpl.billgen.web.form;

import java.time.LocalDateTime;
import java.util.Set;
import com.ritpl.billgen.entity.JobRecurrence;
import com.ritpl.billgen.entity.JobTriggerType;
import com.ritpl.billgen.entity.JobType;
import com.ritpl.billgen.service.JobCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Job scheduling form. Nothing here sends anything by itself. */
public class JobForm {
    @NotBlank @Size(max = 120) private String name;
    @NotNull private JobType jobType = JobType.CUSTOMER_EMAIL;
    @Size(max = 500) private String description;
    private Long notificationId;
    @NotNull private JobTriggerType triggerType = JobTriggerType.MANUAL;
    private LocalDateTime runAt;
    @NotNull private JobRecurrence recurrence = JobRecurrence.NONE;
    private String customerIds = "";
    @Min(1) @Max(500) private int batchSize = 100;
    @Min(0) @Max(10) private int retryLimit = 2;
    @Min(0) @Max(1440) private int retryDelayMinutes = 15;
    @Min(1) @Max(9) private int priority = 5;
    private boolean active = true;

    public JobCommand toCommand() {
        return new JobCommand(name, jobType, description, notificationId, triggerType, runAt, recurrence,
                NotificationForm.parseIds(customerIds), batchSize, retryLimit, retryDelayMinutes, priority, active);
    }

    public String getName(){return name;} public void setName(String v){name=v;}
    public JobType getJobType(){return jobType;} public void setJobType(JobType v){jobType=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public Long getNotificationId(){return notificationId;} public void setNotificationId(Long v){notificationId=v;}
    public JobTriggerType getTriggerType(){return triggerType;} public void setTriggerType(JobTriggerType v){triggerType=v;}
    public LocalDateTime getRunAt(){return runAt;} public void setRunAt(LocalDateTime v){runAt=v;}
    public JobRecurrence getRecurrence(){return recurrence;} public void setRecurrence(JobRecurrence v){recurrence=v;}
    public String getCustomerIds(){return customerIds;} public void setCustomerIds(String v){customerIds=v;}
    public int getBatchSize(){return batchSize;} public void setBatchSize(int v){batchSize=v;}
    public int getRetryLimit(){return retryLimit;} public void setRetryLimit(int v){retryLimit=v;}
    public int getRetryDelayMinutes(){return retryDelayMinutes;} public void setRetryDelayMinutes(int v){retryDelayMinutes=v;}
    public int getPriority(){return priority;} public void setPriority(int v){priority=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
