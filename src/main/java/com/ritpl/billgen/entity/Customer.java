package com.ritpl.billgen.entity;

import java.time.LocalDateTime;
import java.util.Base64;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_name", nullable = false, length = 160) private String companyName;
    @Column(name = "contact_name", nullable = false, length = 120) private String contactName;
    @Column(nullable = false, unique = true, length = 190) private String email;
    @Column(length = 40) private String phone;
    @Column(name = "billing_address", length = 255) private String billingAddress;
    @Column(length = 80) private String city;
    @Column(length = 80) private String state;
    @Column(name = "postal_code", length = 24) private String postalCode;
    @Column(nullable = false, length = 80) private String country = "India";
    @Column(length = 24) private String gstin;
    @Column(name = "logo_data", columnDefinition = "bytea") private byte[] logoData;
    @Column(name = "logo_content_type", length = 40) private String logoContentType;
    @Column(name = "system_account", nullable = false) private boolean systemAccount = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    protected Customer() { }
    public Customer(String companyName, String contactName, String email) { this.companyName = companyName; this.contactName = contactName; this.email = email; }
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
    public String getFullAddress() {
        return java.util.stream.Stream.of(billingAddress, city, state, postalCode, country)
                .filter(v -> v != null && !v.isBlank()).collect(java.util.stream.Collectors.joining(", "));
    }
    public String getLogoDataUrl() {
        return logoData == null || logoData.length == 0 || logoContentType == null ? null
                : "data:" + logoContentType + ";base64," + Base64.getEncoder().encodeToString(logoData);
    }
    public Long getId() { return id; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String v) { companyName = v; }
    public String getContactName() { return contactName; }
    public void setContactName(String v) { contactName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v; }
    public String getBillingAddress() { return billingAddress; }
    public void setBillingAddress(String v) { billingAddress = v; }
    public String getCity() { return city; }
    public void setCity(String v) { city = v; }
    public String getState() { return state; }
    public void setState(String v) { state = v; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String v) { postalCode = v; }
    public String getCountry() { return country; }
    public void setCountry(String v) { country = v; }
    public String getGstin() { return gstin; }
    public void setGstin(String v) { gstin = v; }
    public byte[] getLogoData() { return logoData == null ? null : logoData.clone(); }
    public void setLogoData(byte[] v) { logoData = v == null ? null : v.clone(); }
    public String getLogoContentType() { return logoContentType; }
    public void setLogoContentType(String v) { logoContentType = v; }
    public boolean isSystemAccount() { return systemAccount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
