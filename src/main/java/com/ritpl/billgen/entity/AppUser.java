package com.ritpl.billgen.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 190)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private UserRole role;
    @Column(nullable = false) private boolean enabled = true;
    @OneToOne @JoinColumn(name = "customer_id", unique = true)
    private Customer customer;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    protected AppUser() { }
    public AppUser(String email, String passwordHash, UserRole role) {
        this.email = email.toLowerCase(java.util.Locale.ROOT);
        this.passwordHash = passwordHash;
        this.role = role;
    }
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v == null ? null : v.trim().toLowerCase(java.util.Locale.ROOT); }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { passwordHash = v; }
    public UserRole getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer v) { customer = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
