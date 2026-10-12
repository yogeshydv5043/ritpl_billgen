package com.ritpl.billgen.web.form;

import org.springframework.web.multipart.MultipartFile;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.service.CompanyProfileCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CompanyProfileForm {
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
    private MultipartFile logo;
    private boolean clearLogo;

    public static CompanyProfileForm from(Customer customer) {
        CompanyProfileForm f = new CompanyProfileForm();
        f.companyName = customer.getCompanyName(); f.contactName = customer.getContactName(); f.email = customer.getEmail();
        f.phone = customer.getPhone(); f.billingAddress = customer.getBillingAddress(); f.city = customer.getCity();
        f.state = customer.getState(); f.postalCode = customer.getPostalCode(); f.country = customer.getCountry(); f.gstin = customer.getGstin();
        return f;
    }
    public CompanyProfileCommand toCommand(byte[] data, String contentType) {
        return new CompanyProfileCommand(companyName, contactName, email, phone, billingAddress, city, state, postalCode,
                country, gstin, data, contentType, clearLogo);
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
    public MultipartFile getLogo(){return logo;} public void setLogo(MultipartFile v){logo=v;}
    public boolean isClearLogo(){return clearLogo;} public void setClearLogo(boolean v){clearLogo=v;}
}
