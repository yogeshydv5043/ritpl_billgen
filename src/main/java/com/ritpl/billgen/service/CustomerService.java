package com.ritpl.billgen.service;

import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.repository.CustomerRepository;

@Service
public class CustomerService {
    private final CustomerRepository customers;
    public CustomerService(CustomerRepository customers) { this.customers = customers; }

    @Transactional(readOnly = true)
    public List<Customer> findAll(String search) {
        if (search == null || search.isBlank()) return customers.findAllByOrderByCompanyNameAsc();
        String term = search.trim();
        return customers.findByCompanyNameContainingIgnoreCaseOrContactNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByCompanyNameAsc(term, term, term);
    }
    @Transactional(readOnly = true)
    public Customer findById(long id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
    }
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Customer update(long id, Customer incoming) {
        Customer current = findById(id);
        current.setCompanyName(incoming.getCompanyName()); current.setContactName(incoming.getContactName());
        current.setEmail(incoming.getEmail()); current.setPhone(incoming.getPhone()); current.setBillingAddress(incoming.getBillingAddress());
        current.setCity(incoming.getCity()); current.setState(incoming.getState()); current.setPostalCode(incoming.getPostalCode());
        current.setCountry(incoming.getCountry()); current.setGstin(incoming.getGstin());
        return save(current);
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Customer save(Customer customer) {
        String email = customer.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        boolean duplicate = customer.getId() == null
                ? customers.existsByEmailIgnoreCase(email)
                : customers.existsByEmailIgnoreCaseAndIdNot(email, customer.getId());
        if (duplicate) throw new IllegalArgumentException("A customer with this email already exists.");
        customer.setEmail(email);
        if (customer.getCountry() == null || customer.getCountry().isBlank()) customer.setCountry("India");
        return customers.save(customer);
    }
}
