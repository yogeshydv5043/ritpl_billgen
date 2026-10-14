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
     * Claims up to {@code limit} runnable batches across all running jobs, highest priority first.
     * A batch whose last attempt failed is only runnable again once that job's retry delay has
     * elapsed, which is what paces retries without a sleep or an in-memory timer.
     *
     * <p>The row lock comes from the statement itself ({@code for update skip locked}) rather than
     * from a {@code @Lock} annotation: Hibernate refuses to set a lock mode on a native query, and
     * only {@code SKIP LOCKED} — not {@code PESSIMISTIC_WRITE} — lets a second worker take the next
     * batch instead of waiting on or duplicating the one another transaction is holding. The lock
     * is scoped with {@code of b} so a worker holds a batch row, never the job row, which an
     * administrator may still edit while a run is in flight. The caller must own a transaction that
     * spans claiming and finishing the batch, which {@link com.ritpl.billgen.service.JobProcessor}
     * does through a {@code TransactionTemplate}; the lock is released when that transaction ends.</p>
     */
    /**
     * One batch, read under a row lock, for the failure path. The claim's own write is rolled back with
     * the transaction that failed, so recovery has to look at the row again after the dust settles; the
     * lock is what stops it touching a batch another instance is genuinely working on (JPQL may carry a
     * lock mode, which is why this is not a native query like {@link #claimPending}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from JobBatch b where b.id = :id")
    Optional<JobBatch> findByIdForUpdate(@Param("id") long id);

    @Query(value = "select b.* from job_batches b join background_jobs j on j.id = b.job_id "
            + "where b.status = 'PENDING' and j.status = 'RUNNING' "
            + "and (b.finished_at is null or b.finished_at <= now() - make_interval(mins => j.retry_delay_minutes)) "
            + "order by j.priority desc, b.id asc limit :limit for update of b skip locked", nativeQuery = true)
    List<JobBatch> claimPending(@Param("limit") int limit);
}
