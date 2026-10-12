package com.ritpl.billgen.api;

import com.ritpl.billgen.entity.Customer;

public record CompanyProfileResponse(Long customerId, String companyName, String contactName, String email,
        String phone, String billingAddress, String city, String state, String postalCode, String country,
        String gstin, boolean hasLogo) {
    public static CompanyProfileResponse from(Customer c) {
        return new CompanyProfileResponse(c.getId(), c.getCompanyName(), c.getContactName(), c.getEmail(), c.getPhone(),
                c.getBillingAddress(), c.getCity(), c.getState(), c.getPostalCode(), c.getCountry(), c.getGstin(),
                c.getLogoData() != null && c.getLogoData().length > 0);
    }
}
