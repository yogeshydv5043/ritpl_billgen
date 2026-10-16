package com.ritpl.billgen.repository;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.AuditRecord;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {
    @EntityGraph(attributePaths = "customer")
    java.util.Optional<AuditRecord> findWithCustomerById(Long id);

    @EntityGraph(attributePaths = "customer")
    // The optional filters are typed explicitly. A bare `:param is null` guard sends PostgreSQL an
    // untyped null ("could not determine data type of parameter", and `'%' || ?` planning as bytea
    // concatenation), which would break the audit list for any filter the caller left empty.
    @Query("select a from AuditRecord a where (:customerId is null or a.customer.id = :customerId) "
            + "and (cast(:actor as string) is null or lower(a.actor) like lower(concat('%', cast(:actor as string), '%'))) "
            + "and (:action is null or a.action = :action) "
            + "and (:resourceType is null or a.resourceType = :resourceType) "
            + "and (:outcome is null or a.outcome = :outcome) "
            + "and (cast(:fromTime as timestamp) is null or a.createdAt >= :fromTime) "
            + "and (cast(:untilTime as timestamp) is null or a.createdAt < :untilTime) order by a.createdAt desc")
    Page<AuditRecord> search(@Param("customerId") Long customerId, @Param("actor") String actor,
            @Param("action") String action, @Param("resourceType") String resourceType,
            @Param("outcome") String outcome, @Param("fromTime") Instant fromTime,
            @Param("untilTime") Instant untilTime, Pageable pageable);

    long countByAuditTypeIdAndCreatedAtAfter(Long auditTypeId, java.time.Instant from);

    /** Usage of one audit definition inside a quota window, for a single account. */
    long countByAuditTypeIdAndCustomerIdAndCreatedAtAfter(Long auditTypeId, Long customerId, java.time.Instant from);
    long countByAuditTypeIdAndCustomerIdAndCreatedAtBetween(Long auditTypeId, Long customerId,
            java.time.Instant from, java.time.Instant until);
    long countByCustomerIdAndCreatedAtAfter(Long customerId, java.time.Instant from);

    /**
     * The newest rows inside a window, for a screen that must not write anything. The audit service's
     * paged search deliberately logs an access record of its own, which is right for the audit trail and
     * wrong for a dashboard that is only showing ten lines.
     */
    java.util.List<AuditRecord> findTop10ByCustomerIdAndCreatedAtAfterOrderByCreatedAtDesc(Long customerId,
            Instant after);

    /** Oldest retained instant per definition, shown on the audit configuration screens. */
    @Query("select min(a.createdAt) from AuditRecord a where a.auditTypeId = :auditTypeId")
    java.util.Optional<java.time.Instant> oldestFor(@Param("auditTypeId") Long auditTypeId);

    @Query("select a.auditTypeId, count(a) from AuditRecord a where a.createdAt >= :from group by a.auditTypeId")
    java.util.List<Object[]> countByTypeSince(@Param("from") java.time.Instant from);

    @Query("select count(a) from AuditRecord a where a.customer.id = :customerId")
    long countForCustomer(@Param("customerId") Long customerId);
}
