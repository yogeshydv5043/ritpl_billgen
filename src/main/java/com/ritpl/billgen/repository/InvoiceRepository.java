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

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    @EntityGraph(attributePaths = {"customer", "items", "template"})
    List<Invoice> findAllByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = {"customer", "items", "template"})
    List<Invoice> findTop7ByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = {"customer", "items", "template"})
    List<Invoice> findTop7ByCustomer_IdOrderByCreatedAtDesc(Long customerId);
    @EntityGraph(attributePaths = {"customer", "items", "template"})
    Optional<Invoice> findWithDetailsById(Long id);

    // --- tenant scoped: a customer may only ever load its own invoices -------------------------------------------
    @EntityGraph(attributePaths = {"customer", "items", "template"})
    List<Invoice> findByCustomer_IdOrderByIssueDateDesc(Long customerId);

    @Query("select distinct i from Invoice i left join fetch i.customer left join fetch i.items left join fetch i.template "
            + "where i.id = :id and i.customer.id = :customerId")
    Optional<Invoice> findOwned(@Param("id") Long id, @Param("customerId") Long customerId);

    long countByCustomer_Id(Long customerId);

    long countByCustomer_IdAndStatus(Long customerId, InvoiceStatus status);

    long countByStatus(InvoiceStatus status);
    long countByStatusAndDueDateBefore(InvoiceStatus status, LocalDate date);
    @Query("select sum(i.totalAmount) from Invoice i where i.status = :status")
    BigDecimal sumTotalByStatus(@Param("status") InvoiceStatus status);

    @Query("select coalesce(sum(i.totalAmount), 0) from Invoice i where i.customer.id = :customerId")
    BigDecimal sumTotalByCustomer(@Param("customerId") Long customerId);

    @Query("select max(i.issueDate) from Invoice i where i.customer.id = :customerId")
    Optional<LocalDate> findLastIssueDateForCustomer(@Param("customerId") Long customerId);

    /** Prevents deleting a template that has already been used on an invoice. */
    long countByTemplate_Id(Long templateId);
}