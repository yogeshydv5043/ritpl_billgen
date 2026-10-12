package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.BillTemplate;

public interface BillTemplateRepository extends JpaRepository<BillTemplate, Long> {
    Optional<BillTemplate> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    List<BillTemplate> findAllByOrderBySortOrderAscNameAsc();
    List<BillTemplate> findByActiveTrueOrderBySortOrderAscNameAsc();
    List<BillTemplate> findByIdInOrderBySortOrderAscNameAsc(List<Long> ids);
}