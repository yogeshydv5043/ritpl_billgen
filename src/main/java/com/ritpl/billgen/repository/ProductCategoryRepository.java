package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.ProductCategory;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    // --- admin view (all tenants) -------------------------------------------------------------------------------
    List<ProductCategory> findAllByOrderByNameAsc();

    // --- tenant scoped ------------------------------------------------------------------------------------------
    List<ProductCategory> findByCustomer_IdOrderByNameAsc(Long customerId);

    @Query("select c from ProductCategory c where c.id = :id and c.customer.id = :customerId")
    Optional<ProductCategory> findOwned(@Param("id") Long id, @Param("customerId") Long customerId);

    boolean existsByCustomer_IdAndNameIgnoreCase(Long customerId, String name);

    boolean existsByCustomer_IdAndNameIgnoreCaseAndIdNot(Long customerId, String name, Long id);

    long countByCustomer_Id(Long customerId);
}