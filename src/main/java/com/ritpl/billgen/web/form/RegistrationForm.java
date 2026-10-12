package com.ritpl.billgen.web.form;

import com.ritpl.billgen.service.RegistrationCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationForm {
    @NotBlank @Size(max=160) private String companyName;
    @NotBlank @Size(max=120) private String contactName;
    @NotBlank @Email @Size(max=190) private String email;
    @Size(max=40) private String phone;
    @NotBlank @Size(max=80) private String country = "India";
    @NotBlank @Size(min=8,max=100) private String password;
    public RegistrationCommand toCommand() { return new RegistrationCommand(companyName, contactName, email, phone, country, password); }
    public String getCompanyName(){return companyName;} public void setCompanyName(String v){companyName=v;}
    public String getContactName(){return contactName;} public void setContactName(String v){contactName=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getPhone(){return phone;} public void setPhone(String v){phone=v;}
    public String getCountry(){return country;} public void setCountry(String v){country=v;}
    public String getPassword(){return password;} public void setPassword(String v){password=v;}
}
