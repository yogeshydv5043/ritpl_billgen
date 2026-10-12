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
    boolean existsByCustomer_IdAndNameIgnoreCase(Long customerId, String name);
    boolean existsByCustomer_IdAndNameIgnoreCaseAndIdNot(Long customerId, String name, Long id);
    long countByCustomer_IdAndActiveTrue(Long customerId);
    @Query("select count(i) from Invoice i where i.customTemplate.id = :templateId")
    long countInvoices(@Param("templateId") Long templateId);
}
