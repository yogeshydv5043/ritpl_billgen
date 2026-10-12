package com.ritpl.billgen.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.CatalogProduct;

public interface CatalogProductRepository extends JpaRepository<CatalogProduct, Long> {

    // --- admin views (all tenants) -------------------------------------------------------------------------------
    List<CatalogProduct> findAllByOrderByNameAsc();

    List<CatalogProduct> findByActiveTrueOrderByNameAsc();

    // --- tenant scoped: every customer request must carry the owning customer id --------------------------------
    List<CatalogProduct> findByCustomer_IdOrderByNameAsc(Long customerId);

    List<CatalogProduct> findByCustomer_IdAndActiveTrueOrderByNameAsc(Long customerId);

    // --- ownership lookups ---------------------------------------------------------------------------------------
    @Query("select p from CatalogProduct p where p.id = :id and p.customer.id = :customerId")
    java.util.Optional<CatalogProduct> findOwned(@Param("id") Long id, @Param("customerId") Long customerId);

    boolean existsBySkuIgnoreCaseAndCustomer_Id(String sku, Long customerId);

    boolean existsBySkuIgnoreCaseAndCustomer_IdAndIdNot(String sku, Long customerId, Long id);

    long countByCustomer_Id(Long customerId);

    long countByCustomer_IdAndActiveTrue(Long customerId);

    long countByCategory_Id(Long categoryId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CatalogProduct p set p.category = null where p.category.id = :categoryId and p.customer.id = :customerId")
    int clearCategory(@Param("categoryId") Long categoryId, @Param("customerId") Long customerId);
}