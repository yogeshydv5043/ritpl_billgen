package com.ritpl.billgen.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.AppUser;
import com.ritpl.billgen.repository.AppUserRepository;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;
    public AppUserDetailsService(AppUserRepository users) { this.users = users; }
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        AppUser user = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
        Long customerId = user.getCustomer() == null ? null : user.getCustomer().getId();
        return new AppUserPrincipal(user, customerId);
    }
}
