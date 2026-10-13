package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.JobRecipient;
import com.ritpl.billgen.entity.JobRecipientStatus;

public interface JobRecipientRepository extends JpaRepository<JobRecipient, Long> {
    List<JobRecipient> findByBatch_IdOrderByIdAsc(Long batchId);

    @Query("select r from JobRecipient r where r.job.id = :jobId "
            + "and (:status is null or r.status = :status) order by r.id asc")
    Page<JobRecipient> findForJob(@Param("jobId") Long jobId, @Param("status") JobRecipientStatus status, Pageable pageable);

    Optional<JobRecipient> findByJob_IdAndIdempotencyKey(Long jobId, String idempotencyKey);

    @Query("select r from JobRecipient r where r.batch.id = :batchId and r.status in :statuses order by r.id asc")
    List<JobRecipient> findRetryable(@Param("batchId") Long batchId, @Param("statuses") List<JobRecipientStatus> statuses);

    long countByJob_IdAndStatus(Long jobId, JobRecipientStatus status);
}
