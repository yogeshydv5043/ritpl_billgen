package com.ritpl.billgen.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.CatalogProduct;

public interface CatalogProductRepository extends JpaRepository<CatalogProduct, Long> {
    boolean existsBySkuIgnoreCase(String sku);
    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);
    List<CatalogProduct> findAllByOrderByNameAsc();
    List<CatalogProduct> findByActiveTrueOrderByNameAsc();
}
