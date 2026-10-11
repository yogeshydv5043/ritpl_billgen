package com.ritpl.billgen.repository;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.entity.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    @EntityGraph(attributePaths = {"customer", "items"})
    List<Invoice> findAllByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = {"customer", "items"})
    List<Invoice> findTop7ByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = {"customer", "items"})
    Optional<Invoice> findWithDetailsById(Long id);
    @EntityGraph(attributePaths = {"customer", "items"})
    List<Invoice> findByCustomer_IdOrderByIssueDateDesc(Long customerId);
    long countByStatus(InvoiceStatus status);
    long countByStatusAndDueDateBefore(InvoiceStatus status, LocalDate date);
    @Query("select sum(i.totalAmount) from Invoice i where i.status = :status")
    BigDecimal sumTotalByStatus(@Param("status") InvoiceStatus status);
}
