package com.ritpl.billgen.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.Faq;
import com.ritpl.billgen.entity.FaqType;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    // The type is fetched with the question because both the administrator's table and the public page
    // name it; the filter goes through the id so no join is needed, which is how the audit search works.
    @EntityGraph(attributePaths = "faqType")
    @Query("select f from Faq f where "
            + "(cast(:search as string) is null or lower(f.question) like lower(concat('%', cast(:search as string), '%')) "
            + "     or lower(f.answer) like lower(concat('%', cast(:search as string), '%'))) "
            + "and (:typeId is null or f.faqType.id = :typeId) "
            + "and (:status is null or f.status = :status) "
            + "order by f.displayOrder asc, f.id desc")
    Page<Faq> search(@Param("search") String search, @Param("typeId") Long typeId,
            @Param("status") ContentStatus status, Pageable pageable);

    /**
     * One type's published questions. Fetching per type rather than in one joined query keeps the type out
     * of a lazy proxy on the way to the page, and a hidden type is simply not asked about - which is how a
     * hidden category takes its questions off the public page with it.
     */
    @Query("select f from Faq f where f.faqType = :faqType and f.status = :status "
            + "order by f.displayOrder asc, f.id asc")
    List<Faq> findPublishedForType(@Param("faqType") FaqType faqType, @Param("status") ContentStatus status,
            Pageable limit);

    long countByFaqType_Id(Long faqTypeId);

    @Query("select f.faqType.id, count(f) from Faq f group by f.faqType.id")
    List<Object[]> countsByType();
}
