package com.ritpl.billgen.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Soft-deleted rows are never listed; they exist to keep a record of what was published, not to be
    // edited. The text filter is cast so PostgreSQL does not have to guess a type for an empty filter.
    @Query("select r from Review r where r.deletedAt is null "
            + "and (cast(:search as string) is null or lower(r.reviewerName) like lower(concat('%', cast(:search as string), '%')) "
            + "     or lower(r.reviewContent) like lower(concat('%', cast(:search as string), '%'))) "
            + "and (:status is null or r.status = :status) "
            + "and (:minRating is null or r.rating >= :minRating) "
            + "order by r.displayOrder asc, r.id desc")
    Page<Review> search(@Param("search") String search, @Param("status") ContentStatus status,
            @Param("minRating") Short minRating, Pageable pageable);

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(ContentStatus status);

    /** The public list: published only, in the order an administrator chose. */
    @Query("select r from Review r where r.deletedAt is null and r.status = :status "
            + "order by r.displayOrder asc, r.id desc")
    List<Review> findPublished(@Param("status") ContentStatus status, Pageable limit);
}
