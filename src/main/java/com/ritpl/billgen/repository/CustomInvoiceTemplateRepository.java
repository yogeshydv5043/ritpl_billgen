package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.CustomInvoiceTemplate;

public interface CustomInvoiceTemplateRepository extends JpaRepository<CustomInvoiceTemplate, Long> {
    List<CustomInvoiceTemplate> findByCustomer_IdOrderByNameAsc(Long customerId);
    List<CustomInvoiceTemplate> findByCustomer_IdAndActiveTrueOrderByNameAsc(Long customerId);
    List<CustomInvoiceTemplate> findAllByOrderByCustomer_CompanyNameAscNameAsc();
    Optional<CustomInvoiceTemplate> findByIdAndCustomer_Id(Long id, Long customerId);
    // A design retired by "delete" (kept only because invoices were issued with it) is neither a name in
    // use nor a used slot, so every name and count query here ignores the retired rows.
    boolean existsByCustomer_IdAndNameIgnoreCaseAndDeletedAtIsNull(Long customerId, String name);
    boolean existsByCustomer_IdAndNameIgnoreCaseAndIdNotAndDeletedAtIsNull(Long customerId, String name, Long id);
    long countByCustomer_IdAndActiveTrue(Long customerId);
    long countByCustomer_IdAndDeletedAtIsNull(Long customerId);
    @Query("select count(i) from Invoice i where i.customTemplate.id = :templateId")
    long countInvoices(@Param("templateId") Long templateId);
}
