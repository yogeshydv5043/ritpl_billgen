package com.ritpl.billgen.web.form;

import com.ritpl.billgen.entity.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CustomerForm {
    @NotBlank @Size(max = 160) private String companyName;
    @NotBlank @Size(max = 120) private String contactName;
    @NotBlank @Email @Size(max = 190) private String email;
    @Size(max = 40) private String phone;
    @Size(max = 255) private String billingAddress;
    @Size(max = 80) private String city;
    @Size(max = 80) private String state;
    @Size(max = 24) private String postalCode;
    @NotBlank @Size(max = 80) private String country = "India";
    @Pattern(regexp = "(?i)^$|[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][A-Z0-9]Z[A-Z0-9]$", message = "Enter a valid 15-character GSTIN.")
    @Size(max = 24) private String gstin;

    public static CustomerForm from(Customer c) {
        CustomerForm f = new CustomerForm(); f.companyName=c.getCompanyName(); f.contactName=c.getContactName(); f.email=c.getEmail();
        f.phone=c.getPhone(); f.billingAddress=c.getBillingAddress(); f.city=c.getCity(); f.state=c.getState();
        f.postalCode=c.getPostalCode(); f.country=c.getCountry(); f.gstin=c.getGstin(); return f;
    }
    public Customer toEntity() {
        Customer c = new Customer(companyName == null ? null : companyName.trim(), contactName == null ? null : contactName.trim(), email == null ? null : email.trim());
        c.setPhone(clean(phone)); c.setBillingAddress(clean(billingAddress)); c.setCity(clean(city)); c.setState(clean(state));
        c.setPostalCode(clean(postalCode)); c.setCountry(country == null ? "India" : country.trim()); c.setGstin(clean(gstin)); return c;
    }
    private static String clean(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    public String getCompanyName(){return companyName;} public void setCompanyName(String v){companyName=v;}
    public String getContactName(){return contactName;} public void setContactName(String v){contactName=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getPhone(){return phone;} public void setPhone(String v){phone=v;}
    public String getBillingAddress(){return billingAddress;} public void setBillingAddress(String v){billingAddress=v;}
    public String getCity(){return city;} public void setCity(String v){city=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public String getPostalCode(){return postalCode;} public void setPostalCode(String v){postalCode=v;}
    public String getCountry(){return country;} public void setCountry(String v){country=v;}
    public String getGstin(){return gstin;} public void setGstin(String v){gstin=v;}
}
