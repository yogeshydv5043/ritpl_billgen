package com.ritpl.billgen.service;

/**
 * Details captured when an administrator creates or edits a customer account, including the login
 * used by that account to sign in.
 */
public record CustomerAccountCommand(String companyName, String contactName, String email, String phone,
                                     String billingAddress, String city, String state, String postalCode,
                                     String country, String gstin, String loginEmail, String password,
                                     Boolean loginEnabled) { }