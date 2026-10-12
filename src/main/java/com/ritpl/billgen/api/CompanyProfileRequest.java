package com.ritpl.billgen.api;

import com.ritpl.billgen.service.CompanyProfileCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyProfileRequest(@NotBlank @Size(max=160) String companyName,
        @NotBlank @Size(max=120) String contactName, @NotBlank @Email @Size(max=190) String email,
        @Size(max=40) String phone, @Size(max=255) String billingAddress, @Size(max=80) String city,
        @Size(max=80) String state, @Size(max=24) String postalCode, @Size(max=80) String country,
        @Size(max=24) String gstin) {
    public CompanyProfileCommand toCommand() { return new CompanyProfileCommand(companyName, contactName, email, phone,
            billingAddress, city, state, postalCode, country, gstin, null, null, false); }
}
