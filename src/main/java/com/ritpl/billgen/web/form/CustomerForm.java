package com.ritpl.billgen.web.form;

import com.ritpl.billgen.entity.AppUser;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.service.CustomerAccountCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CustomerForm {
    @NotBlank @Size(max=160) private String companyName;
    @NotBlank @Size(max=120) private String contactName;
    @NotBlank @Email @Size(max=190) private String email;
    @Size(max=40) private String phone;
    @Size(max=255) private String billingAddress;
    @Size(max=80) private String city;
    @Size(max=80) private String state;
    @Size(max=24) private String postalCode;
    @Size(max=80) private String country = "India";
    @Size(max=24) private String gstin;
    @Email @Size(max=190) private String loginEmail;
    @Size(max=100) private String password;
    private boolean loginEnabled = true;
    private boolean loginExists;
    /** Account availability, edited alongside the profile. */
    private String status = "ACTIVE";
    /** Optional subscription assignment at creation time. */
    private Long planId;
    private java.time.LocalDate startedOn;
    private java.time.LocalDate endsOn;

    public static CustomerForm from(Customer customer, AppUser login) {
        CustomerForm form = new CustomerForm();
        form.companyName = customer.getCompanyName();
        form.contactName = customer.getContactName();
        form.email = customer.getEmail();
        form.phone = customer.getPhone();
        form.billingAddress = customer.getBillingAddress();
        form.city = customer.getCity();
        form.state = customer.getState();
        form.postalCode = customer.getPostalCode();
        form.country = customer.getCountry();
        form.gstin = customer.getGstin();
        form.status = customer.getStatus().name();
        if (login != null) {
            form.loginExists = true;
            form.loginEmail = login.getEmail();
            form.loginEnabled = login.isEnabled();
        } else {
            form.loginEmail = customer.getEmail();
            form.loginEnabled = true;
        }
        return form;
    }

    public CustomerAccountCommand toCommand() {
        return new CustomerAccountCommand(companyName, contactName, email, phone, billingAddress, city, state,
                postalCode, country, gstin, loginEmail, password, loginEnabled);
    }

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
    public String getLoginEmail(){return loginEmail;} public void setLoginEmail(String v){loginEmail=v;}
    public String getPassword(){return password;} public void setPassword(String v){password=v;}
    public boolean isLoginEnabled(){return loginEnabled;} public void setLoginEnabled(boolean v){loginEnabled=v;}
    public boolean isLoginExists(){return loginExists;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Long getPlanId(){return planId;} public void setPlanId(Long v){planId=v;}
    public java.time.LocalDate getStartedOn(){return startedOn;} public void setStartedOn(java.time.LocalDate v){startedOn=v;}
    public java.time.LocalDate getEndsOn(){return endsOn;} public void setEndsOn(java.time.LocalDate v){endsOn=v;}
    public boolean isDisabled(){return "DISABLED".equalsIgnoreCase(status);}
}