package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<Customer> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    List<Customer> findBySystemAccountFalseOrderByCompanyNameAsc();

    @Query("select c from Customer c where c.systemAccount = false and "
            + "(lower(c.companyName) like lower(concat('%', concat(:term, '%'))) "
            + "or lower(c.contactName) like lower(concat('%', concat(:term, '%'))) "
            + "or lower(c.email) like lower(concat('%', concat(:term, '%')))) order by c.companyName asc")
    List<Customer> searchVisible(@Param("term") String term);

    long countBySystemAccountFalse();
}