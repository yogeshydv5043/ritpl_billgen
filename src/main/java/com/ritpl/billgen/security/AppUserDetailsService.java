package com.ritpl.billgen.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.AccountStatus;
import com.ritpl.billgen.entity.AppUser;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.repository.AppUserRepository;
import com.ritpl.billgen.repository.CustomerRepository;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;
    private final CustomerRepository customers;
    public AppUserDetailsService(AppUserRepository users, CustomerRepository customers) {
        this.users = users; this.customers = customers;
    }
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        AppUser user = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
        Customer customer = user.getCustomer();
        Long customerId = customer == null ? null : customer.getId();
        // An administrator login has no account behind it. For a customer login the account decides:
        // a disabled or soft-deleted account cannot authenticate even if its credential is enabled.
        boolean usable = customer == null
                || (!customer.isDeleted() && customer.getStatus() == AccountStatus.ACTIVE);
        return new AppUserPrincipal(user, customerId, usable);
    }
}
