package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.SubscriptionPlan;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    boolean existsByNameIgnoreCase(String name);

    /** A plan can only be retired once no live account still depends on it. */
    @Query("select count(s) from CustomerSubscription s join s.customer c join s.plan p "
            + "where p.id = :planId and s.status = com.ritpl.billgen.entity.SubscriptionStatus.ACTIVE "
            + "and c.deletedAt is null")
    long countActiveSubscriptions(@Param("planId") Long planId);

    @Query("select count(distinct s.customer.id) from CustomerSubscription s where s.plan.id = :planId")
    long countAssignedCustomers(@Param("planId") Long planId);
    List<SubscriptionPlan> findAllByOrderBySortOrderAscNameAsc();
    List<SubscriptionPlan> findByDeletedAtIsNullOrderBySortOrderAscNameAsc();
    List<SubscriptionPlan> findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscNameAsc();
    Optional<SubscriptionPlan> findByIdAndDeletedAtIsNull(Long id);
    long countByActiveTrueAndDeletedAtIsNull();
    List<SubscriptionPlan> findByActiveTrueOrderBySortOrderAscNameAsc();

    /** Prevents deleting a template that is still bundled with a plan. */
    @Query("select count(p) from SubscriptionPlan p join p.templates t where t.id = :templateId")
    long countByTemplate(@Param("templateId") Long templateId);
}