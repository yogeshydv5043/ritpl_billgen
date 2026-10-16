package com.ritpl.billgen.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.BackgroundJob;
import com.ritpl.billgen.entity.JobStatus;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, Long> {
    List<BackgroundJob> findByStatusOrderByPriorityDescRunAtAsc(JobStatus status);

    @Query("select j from BackgroundJob j where (:status is null or j.status = :status) "
            + "and (cast(:search as string) is null or lower(j.name) like lower(concat('%', cast(:search as string), '%'))) "
            + "and (cast(:dueBefore as timestamp) is null or j.runAt <= :dueBefore) order by j.priority desc, j.runAt asc")
    Page<BackgroundJob> search(@Param("status") JobStatus status, @Param("search") String search,
            @Param("dueBefore") Instant dueBefore, Pageable pageable);

    /** Due = scheduled/pending whose run time has arrived. */
    @Query("select j from BackgroundJob j where j.status in :statuses and j.runAt <= :now order by j.priority desc, j.runAt asc")
    List<BackgroundJob> findDue(@Param("statuses") List<JobStatus> statuses, @Param("now") Instant now);

    long countByStatus(JobStatus status);
    long countByStatusIn(List<JobStatus> statuses);
}
