package com.ritpl.billgen.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.CatalogProduct;

public interface CatalogProductRepository extends JpaRepository<CatalogProduct, Long>, JpaSpecificationExecutor<CatalogProduct> {

    // --- admin views (all tenants) -------------------------------------------------------------------------------
    List<CatalogProduct> findAllByOrderByNameAsc();

    List<CatalogProduct> findByActiveTrueOrderByNameAsc();

    // --- tenant scoped: every customer request must carry the owning customer id --------------------------------
    List<CatalogProduct> findByCustomer_IdOrderByNameAsc(Long customerId);

    List<CatalogProduct> findByCustomer_IdAndActiveTrueOrderByNameAsc(Long customerId);

    // --- ownership lookups ---------------------------------------------------------------------------------------
    /** The items this account touched most recently, for its dashboard. */
    List<CatalogProduct> findByCustomer_IdOrderByUpdatedAtDesc(Long customerId,
            org.springframework.data.domain.Pageable pageable);

    @Query("select p from CatalogProduct p where p.id = :id and p.customer.id = :customerId")
    java.util.Optional<CatalogProduct> findOwned(@Param("id") Long id, @Param("customerId") Long customerId);

    boolean existsBySkuIgnoreCaseAndCustomer_Id(String sku, Long customerId);

    boolean existsBySkuIgnoreCaseAndCustomer_IdAndIdNot(String sku, Long customerId, Long id);

    long countByCustomer_Id(Long customerId);

    long countByCustomer_IdAndActiveTrue(Long customerId);

    long countByCategory_Id(Long categoryId);

    /** How many items sit in each category of one account, for the category list's counts. */
    @Query("select p.category.id, count(p) from CatalogProduct p where p.customer.id = :customerId "
            + "and p.category.id is not null group by p.category.id")
    List<Object[]> countGroupedByCategory(@Param("customerId") Long customerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CatalogProduct p set p.category = null where p.category.id = :categoryId and p.customer.id = :customerId")
    int clearCategory(@Param("categoryId") Long categoryId, @Param("customerId") Long customerId);
}