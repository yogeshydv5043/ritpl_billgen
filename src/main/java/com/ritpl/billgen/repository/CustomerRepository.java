package com.ritpl.billgen.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    boolean existsByEmailIgnoreCase(String email);
    java.util.Optional<Customer> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    List<Customer> findAllByOrderByCompanyNameAsc();
    List<Customer> findByCompanyNameContainingIgnoreCaseOrContactNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByCompanyNameAsc(String company, String contact, String email);
}
