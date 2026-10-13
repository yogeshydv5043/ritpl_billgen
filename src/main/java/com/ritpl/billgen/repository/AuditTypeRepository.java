package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.AuditType;

public interface AuditTypeRepository extends JpaRepository<AuditType, Long> {
    List<AuditType> findByDeletedAtIsNullOrderByEventCategoryAscNameAsc();
    List<AuditType> findByActiveTrueAndDeletedAtIsNullOrderByEventCategoryAscNameAsc();
    Optional<AuditType> findByCodeAndDeletedAtIsNull(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    long countByActiveTrueAndDeletedAtIsNull();

    /** Plans that still depend on a definition, used to refuse a destructive change. */
    @Query("select count(p) from SubscriptionPlan p join p.auditTypes t where t.id = :auditTypeId")
    long countPlansUsing(@Param("auditTypeId") Long auditTypeId);
}
