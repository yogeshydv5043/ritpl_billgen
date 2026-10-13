package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.BillTemplate;

public interface BillTemplateRepository extends JpaRepository<BillTemplate, Long> {
    Optional<BillTemplate> findByCodeIgnoreCase(String code);
    List<BillTemplate> findByDeletedAtIsNullOrderBySortOrderAscNameAsc();
    List<BillTemplate> findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscNameAsc();
    Optional<BillTemplate> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(String code, Long id);
    boolean existsByNameIgnoreCaseAndIdNotAndDeletedAtIsNull(String name, Long id);
    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);
    long countByActiveTrueAndDeletedAtIsNull();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    List<BillTemplate> findAllByOrderBySortOrderAscNameAsc();
    List<BillTemplate> findByActiveTrueOrderBySortOrderAscNameAsc();
    List<BillTemplate> findByIdInOrderBySortOrderAscNameAsc(List<Long> ids);
}