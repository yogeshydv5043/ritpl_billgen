package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.JobBatch;
import jakarta.persistence.LockModeType;

public interface JobBatchRepository extends JpaRepository<JobBatch, Long> {
    List<JobBatch> findByJob_IdOrderByBatchNumberAsc(Long jobId);
    long countByJob_IdAndStatus(Long jobId, com.ritpl.billgen.entity.JobBatchStatus status);

    /**
     * Claims the next batch of one job. SKIP LOCKED means a concurrent worker skips rows another
     * transaction is already holding, so a batch is never processed twice and nothing is queued in
     * memory that a restart would lose.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "select * from job_batches where status = 'PENDING' and job_id = :jobId "
            + "order by batch_number asc limit 1 for update skip locked", nativeQuery = true)
    Optional<JobBatch> claimNextForJob(@Param("jobId") Long jobId);

    /**
     * Claims up to {@code limit} runnable batches across all running jobs, highest priority first.
     * A batch whose last attempt failed is only runnable again once that job's retry delay has
     * elapsed, which is what paces retries without a sleep or an in-memory timer.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "select b.* from job_batches b join background_jobs j on j.id = b.job_id "
            + "where b.status = 'PENDING' and j.status = 'RUNNING' "
            + "and (b.finished_at is null or b.finished_at <= now() - make_interval(mins => j.retry_delay_minutes)) "
            + "order by j.priority desc, b.id asc limit :limit for update skip locked", nativeQuery = true)
    List<JobBatch> claimPending(@Param("limit") int limit);
}
