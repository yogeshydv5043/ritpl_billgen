package com.ritpl.billgen.api;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.service.CatalogService;
import com.ritpl.billgen.service.CustomerService;
import com.ritpl.billgen.service.InvoiceService;
import com.ritpl.billgen.service.PlanService;
import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/v1")
public class InvoiceApiController {
    private final InvoiceService invoices; private final CustomerService customers; private final CatalogService catalog; private final PlanService plans;
    public InvoiceApiController(InvoiceService invoices,CustomerService customers,CatalogService catalog,PlanService plans){this.invoices=invoices;this.customers=customers;this.catalog=catalog;this.plans=plans;}

    @GetMapping("/invoices")
    public List<InvoiceResponse> invoices(@RequestParam(required=false) Long customerId){
        var results=customerId==null?invoices.findAll():invoices.findForCustomer(customerId);
        return results.stream().map(InvoiceResponse::from).toList();
    }
    @GetMapping("/invoices/{id}") public InvoiceResponse invoice(@PathVariable long id){return InvoiceResponse.from(invoices.findById(id));}
    @PostMapping("/invoices") public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody CreateInvoiceRequest request){
        if (request.initialStatus() == InvoiceStatus.PAID) throw new IllegalArgumentException("An API invoice must start as DRAFT or SENT.");
        var saved=invoices.create(request.toCommand());
        URI location=ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(location).body(InvoiceResponse.from(invoices.findById(saved.getId())));
    }
    @PatchMapping("/invoices/{id}/status") public InvoiceResponse status(@PathVariable long id,@Valid @RequestBody InvoiceStatusRequest request){
        var invoice=invoices.findById(id);
        if(request.status()==InvoiceStatus.SENT && invoice.getStatus()==InvoiceStatus.DRAFT) invoice=invoices.markSent(id);
        else if(request.status()==InvoiceStatus.PAID && invoice.getStatus()==InvoiceStatus.SENT) invoice=invoices.markPaid(id);
        else if(request.status()!=invoice.getStatus()) throw new IllegalArgumentException("Status can only move from DRAFT to SENT and from SENT to PAID.");
        return InvoiceResponse.from(invoices.findById(id));
    }
    @GetMapping("/customers") public List<CustomerResponse> customers(){return customers.findAll(null).stream().map(CustomerResponse::from).toList();}
    @GetMapping("/customers/{id}") public CustomerResponse customer(@PathVariable long id){return CustomerResponse.from(customers.findById(id));}
    @GetMapping("/products") public List<ProductResponse> products(){return catalog.findActive().stream().map(ProductResponse::from).toList();}
    @GetMapping("/plans") public List<PlanResponse> plans(){return plans.findActive().stream().map(PlanResponse::from).toList();}
}
