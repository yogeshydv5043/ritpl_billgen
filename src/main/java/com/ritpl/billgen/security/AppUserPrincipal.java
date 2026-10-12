package com.ritpl.billgen.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.ritpl.billgen.entity.AppUser;

public class AppUserPrincipal implements UserDetails {
    private static final long serialVersionUID = 1L;
    private final Long id;
    private final Long customerId;
    private final String email;
    private final String passwordHash;
    private final String role;
    private final boolean enabled;

    public AppUserPrincipal(AppUser user, Long customerId) {
        this.id = user.getId(); this.customerId = customerId; this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash(); this.role = user.getRole().name(); this.enabled = user.isEnabled();
    }
    public Long getId() { return id; }
    /** The customer account this login belongs to, or {@code null} for an administrator. */
    public Long getCustomerId() { return customerId; }
    public String getRole() { return role; }
    public boolean isAdmin() { return "ADMIN".equals(role); }
    /** @throws AccessDeniedException when a customer login has no account attached. */
    public long requireCustomerId() {
        if (customerId == null) {
            throw new AccessDeniedException("This login is not linked to a customer account.");
        }
        return customerId;
    }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + role)); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}