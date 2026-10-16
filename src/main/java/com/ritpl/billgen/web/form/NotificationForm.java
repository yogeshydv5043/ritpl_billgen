package com.ritpl.billgen.web.form;

import java.util.LinkedHashSet;
import java.util.Set;
import com.ritpl.billgen.entity.Notification;
import com.ritpl.billgen.entity.NotificationChannel;
import com.ritpl.billgen.entity.NotificationRecipientType;
import com.ritpl.billgen.entity.NotificationStatusFilter;
import com.ritpl.billgen.entity.NotificationTriggerEvent;
import com.ritpl.billgen.service.NotificationCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dynamic notification editor. Placeholders are validated server-side before activation. */
public class NotificationForm {
    @NotBlank @Size(max = 120) private String name;
    @NotBlank @Size(max = 190) private String title;
    @NotBlank @Size(max = 4000) private String body;
    @NotNull private NotificationChannel channel = NotificationChannel.IN_APP;
    @NotNull private NotificationTriggerEvent triggerEvent = NotificationTriggerEvent.MANUAL;
    @NotNull private NotificationRecipientType recipientType = NotificationRecipientType.CUSTOMERS;
    @NotNull private NotificationStatusFilter statusFilter = NotificationStatusFilter.ACTIVE_ONLY;
    private String planIds = "";
    @Size(max = 64) private String scheduleExpression;
    private boolean active;

    public static NotificationForm from(Notification notification) {
        NotificationForm form = new NotificationForm();
        form.name = notification.getName();
        form.title = notification.getTitle();
        form.body = notification.getBody();
        form.channel = notification.getChannel();
        form.triggerEvent = notification.getTriggerEvent();
        form.recipientType = notification.getRecipientType();
        form.statusFilter = notification.getStatusFilter();
        form.planIds = notification.getPlanFilter() == null ? "" : notification.getPlanFilter();
        form.scheduleExpression = notification.getScheduleExpression();
        form.active = notification.isActive();
        return form;
    }

    public NotificationCommand toCommand() {
        return new NotificationCommand(name, title, body, channel, triggerEvent, recipientType, statusFilter,
                parseIds(planIds), scheduleExpression, active);
    }

    static Set<Long> parseIds(String csv) {
        Set<Long> ids = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) return ids;
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.matches("\\d{1,19}")) ids.add(Long.valueOf(trimmed));
        }
        return ids;
    }

    public String getName(){return name;} public void setName(String v){name=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getBody(){return body;} public void setBody(String v){body=v;}
    public NotificationChannel getChannel(){return channel;} public void setChannel(NotificationChannel v){channel=v;}
    public NotificationTriggerEvent getTriggerEvent(){return triggerEvent;} public void setTriggerEvent(NotificationTriggerEvent v){triggerEvent=v;}
    public NotificationRecipientType getRecipientType(){return recipientType;} public void setRecipientType(NotificationRecipientType v){recipientType=v;}
    public NotificationStatusFilter getStatusFilter(){return statusFilter;} public void setStatusFilter(NotificationStatusFilter v){statusFilter=v;}
    public String getPlanIds(){return planIds;} public void setPlanIds(String v){planIds=v;}
    public String getScheduleExpression(){return scheduleExpression;} public void setScheduleExpression(String v){scheduleExpression=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
