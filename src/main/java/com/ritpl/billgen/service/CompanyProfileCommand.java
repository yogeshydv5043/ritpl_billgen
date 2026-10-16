package com.ritpl.billgen.service;

public record CompanyProfileCommand(String companyName, String contactName, String email, String phone,
        String billingAddress, String city, String state, String postalCode, String country, String gstin,
        byte[] logoData, String logoContentType, boolean clearLogo) { }
