package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.FaqType;

public interface FaqTypeRepository extends JpaRepository<FaqType, Long> {
    Optional<FaqType> findByTypeCodeIgnoreCase(String typeCode);
    boolean existsByTypeCodeIgnoreCase(String typeCode);
    boolean existsByTypeCodeIgnoreCaseAndIdNot(String typeCode, Long id);
    List<FaqType> findAllByOrderByDisplayOrderAscTypeNameAsc();
    List<FaqType> findByStatusOrderByDisplayOrderAscTypeNameAsc(ContentStatus status);
}
