package com.ritpl.billgen.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.SubscriptionPlan;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    List<SubscriptionPlan> findAllByOrderBySortOrderAscNameAsc();
    List<SubscriptionPlan> findByActiveTrueOrderBySortOrderAscNameAsc();
}
