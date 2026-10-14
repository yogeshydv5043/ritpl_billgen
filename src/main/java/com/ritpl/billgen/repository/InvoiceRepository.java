package com.ritpl.billgen.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.entity.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Invoice> {
    @EntityGraph(attributePaths = {"customer", "items", "template", "customTemplate", "usageSubscription"})
    List<Invoice> findAllByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = {"customer", "items", "template", "customTemplate", "usageSubscription"})
    List<Invoice> findTop7ByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = {"customer", "items", "template", "customTemplate", "usageSubscription"})
    List<Invoice> findTop7ByCustomer_IdOrderByCreatedAtDesc(Long customerId);
    @EntityGraph(attributePaths = {"customer", "items", "template", "customTemplate", "usageSubscription"})
    Optional<Invoice> findWithDetailsById(Long id);

    // --- tenant scoped: a customer may only ever load its own invoices -------------------------------------------
    /** The most recent invoices of one account, newest issue date first, for its dashboard. */
    List<Invoice> findByCustomer_IdOrderByIssueDateDesc(Long customerId, org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "items", "template", "customTemplate", "usageSubscription"})
    List<Invoice> findByCustomer_IdOrderByIssueDateDesc(Long customerId);

    @Query("select distinct i from Invoice i left join fetch i.customer left join fetch i.items left join fetch i.template left join fetch i.customTemplate left join fetch i.usageSubscription "
            + "where i.id = :id and i.customer.id = :customerId")
    Optional<Invoice> findOwned(@Param("id") Long id, @Param("customerId") Long customerId);

    long countByCustomer_Id(Long customerId);
    long countByCustomer_IdAndUsageCountedTrue(Long customerId);

    long countByCustomer_IdAndStatus(Long customerId, InvoiceStatus status);

    long countByStatus(InvoiceStatus status);
    long countByStatusAndDueDateBefore(InvoiceStatus status, LocalDate date);
    @Query("select sum(i.totalAmount) from Invoice i where i.status = :status")
    BigDecimal sumTotalByStatus(@Param("status") InvoiceStatus status);

    @Query("select coalesce(sum(i.totalAmount), 0) from Invoice i where i.customer.id = :customerId and (i.status = com.ritpl.billgen.entity.InvoiceStatus.SENT or i.status = com.ritpl.billgen.entity.InvoiceStatus.PAID)")
    BigDecimal sumTotalByCustomer(@Param("customerId") Long customerId);

    @Query("select max(i.issueDate) from Invoice i where i.customer.id = :customerId and (i.status = com.ritpl.billgen.entity.InvoiceStatus.SENT or i.status = com.ritpl.billgen.entity.InvoiceStatus.PAID)")
    Optional<LocalDate> findLastIssueDateForCustomer(@Param("customerId") Long customerId);

    /** Counts historically charged invoice slots, including sent invoices later cancelled. */
    long countByUsageSubscription_IdAndUsageCountedTrue(Long subscriptionId);

    @EntityGraph(attributePaths = {"customer", "items", "template", "customTemplate", "usageSubscription"})
    List<Invoice> findByGeneratedAtGreaterThanEqualAndGeneratedAtLessThanOrderByGeneratedAtAsc(java.time.Instant from, java.time.Instant until);

    @EntityGraph(attributePaths = {"customer", "items", "template", "customTemplate", "usageSubscription"})
    List<Invoice> findByCustomer_IdAndGeneratedAtGreaterThanEqualAndGeneratedAtLessThanOrderByGeneratedAtAsc(
            Long customerId, java.time.Instant from, java.time.Instant until);

    /** Prevents deleting a template that has already been used on an invoice. */
    long countByTemplate_Id(Long templateId);

    // --- paged, filtered listings -------------------------------------------------------------------------------
    /**
     * The one invoice listing that combines the optional filters, sorting and paging. The owner is
     * fetched with the rows because the list shows the buyer and open-in-view is off, while line items
     * are deliberately not fetched: a collection fetch would page the result set in memory.
     */
    @EntityGraph(attributePaths = {"customer"})
    org.springframework.data.domain.Page<Invoice> findAll(
            org.springframework.data.jpa.domain.Specification<Invoice> spec,
            org.springframework.data.domain.Pageable pageable);

    /** Invoices issued inside a period, for the dashboard's "this month" card. */
    long countByCustomer_IdAndIssueDateGreaterThanEqualAndIssueDateLessThan(
            Long customerId, java.time.LocalDate from, java.time.LocalDate until);

    long countByCustomer_IdAndStatusIn(Long customerId, java.util.Collection<InvoiceStatus> statuses);

    /** Total of the account's issued invoices in a period, for the dashboard's money figure. */
    @Query("select coalesce(sum(i.totalAmount), 0) from Invoice i where i.customer.id = :customerId "
            + "and i.issueDate >= :from and i.issueDate < :until "
            + "and (i.status = com.ritpl.billgen.entity.InvoiceStatus.SENT or i.status = com.ritpl.billgen.entity.InvoiceStatus.PAID)")
    BigDecimal sumTotalByCustomerBetween(@Param("customerId") Long customerId, @Param("from") java.time.LocalDate from,
            @Param("until") java.time.LocalDate until);
}