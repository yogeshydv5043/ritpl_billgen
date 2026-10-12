package com.ritpl.billgen.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.SubscriptionPlan;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    List<SubscriptionPlan> findAllByOrderBySortOrderAscNameAsc();
    List<SubscriptionPlan> findByActiveTrueOrderBySortOrderAscNameAsc();

    /** Prevents deleting a template that is still bundled with a plan. */
    @Query("select count(p) from SubscriptionPlan p join p.templates t where t.id = :templateId")
    long countByTemplate(@Param("templateId") Long templateId);
}