package com.ritpl.billgen.web.form;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.service.CreateInvoiceCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Invoice form of a customer account. The owning customer id is never submitted: it is taken from
 * the authenticated session, so a customer cannot post an invoice into another account.
 */
public class InvoiceForm {
    @NotNull private Long templateId;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate issueDate = LocalDate.now();
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate dueDate = LocalDate.now().plusDays(14);
    @Size(max = 1200) private String notes;
    @Pattern(regexp = "[A-Za-z]{3}") private String currency = "INR";
    @NotNull private InvoiceStatus initialStatus = InvoiceStatus.SENT;
    @Valid @NotEmpty @Size(max = 30) private List<InvoiceLineForm> lines = new ArrayList<>(List.of(new InvoiceLineForm()));

    /** The account is resolved from the session by the service; customerId is sent as null. */
    public CreateInvoiceCommand toCommand() {
        return new CreateInvoiceCommand(null, issueDate, dueDate, notes, currency, templateId,
                lines.stream().map(InvoiceLineForm::toInput).collect(Collectors.toList()), initialStatus);
    }

    public Long getTemplateId(){return templateId;} public void setTemplateId(Long v){templateId=v;}
    public LocalDate getIssueDate(){return issueDate;} public void setIssueDate(LocalDate v){issueDate=v;}
    public LocalDate getDueDate(){return dueDate;} public void setDueDate(LocalDate v){dueDate=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;}
    public InvoiceStatus getInitialStatus(){return initialStatus;} public void setInitialStatus(InvoiceStatus v){initialStatus=v;}
    public List<InvoiceLineForm> getLines(){return lines;} public void setLines(List<InvoiceLineForm> v){lines=v;}
}