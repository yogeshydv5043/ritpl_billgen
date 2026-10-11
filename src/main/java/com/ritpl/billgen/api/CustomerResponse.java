package com.ritpl.billgen.api;

import java.time.LocalDateTime;
import com.ritpl.billgen.entity.Customer;

public record CustomerResponse(Long id,String companyName,String contactName,String email,String phone,
        String billingAddress,String city,String state,String postalCode,String country,String gstin,LocalDateTime createdAt) {
    public static CustomerResponse from(Customer c){return new CustomerResponse(c.getId(),c.getCompanyName(),c.getContactName(),c.getEmail(),c.getPhone(),c.getBillingAddress(),c.getCity(),c.getState(),c.getPostalCode(),c.getCountry(),c.getGstin(),c.getCreatedAt());}
}
