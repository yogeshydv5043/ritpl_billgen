package com.ritpl.billgen.service;

public record RegistrationCommand(String companyName, String contactName, String email, String phone,
        String country, String password) { }
