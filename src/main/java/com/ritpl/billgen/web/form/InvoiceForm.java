package com.ritpl.billgen.web.form;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.service.CreateInvoiceCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class InvoiceForm {
    private Long templateId;
    private Long customTemplateId;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate issueDate = LocalDate.now();
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate dueDate = LocalDate.now().plusDays(14);
    @Size(max = 1200) private String notes;
    @Pattern(regexp = "[A-Za-z]{3}") private String currency = "INR";
    @NotNull private InvoiceStatus initialStatus = InvoiceStatus.DRAFT;
    @DecimalMin("0.00") @Digits(integer=12, fraction=2) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Valid @NotEmpty @Size(max = 30) private List<InvoiceLineForm> lines = new ArrayList<>(List.of(new InvoiceLineForm()));

    public CreateInvoiceCommand toCommand() {
        return new CreateInvoiceCommand(null, issueDate, dueDate, notes, currency, templateId,
                lines.stream().map(InvoiceLineForm::toInput).collect(Collectors.toList()), initialStatus,
                customTemplateId, discountAmount);
    }
    public Long getTemplateId(){return templateId;} public void setTemplateId(Long v){templateId=v;}
    public Long getCustomTemplateId(){return customTemplateId;} public void setCustomTemplateId(Long v){customTemplateId=v;}
    public LocalDate getIssueDate(){return issueDate;} public void setIssueDate(LocalDate v){issueDate=v;}
    public LocalDate getDueDate(){return dueDate;} public void setDueDate(LocalDate v){dueDate=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;}
    public InvoiceStatus getInitialStatus(){return initialStatus;} public void setInitialStatus(InvoiceStatus v){initialStatus=v;}
    public BigDecimal getDiscountAmount(){return discountAmount;} public void setDiscountAmount(BigDecimal v){discountAmount=v;}
    public List<InvoiceLineForm> getLines(){return lines;} public void setLines(List<InvoiceLineForm> v){lines=v;}
}
