package com.ritpl.billgen.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.JobStatus;
import com.ritpl.billgen.repository.BackgroundJobRepository;

/** Small aggregate read used by the shell badge. */
@Service
public class BackgroundJobQueryService {
    private final BackgroundJobRepository jobs;
    public BackgroundJobQueryService(BackgroundJobRepository jobs) { this.jobs = jobs; }

    @Transactional(readOnly = true)
    public long attentionCount() {
        return jobs.countByStatus(JobStatus.FAILED) + jobs.countByStatus(JobStatus.PARTIALLY_FAILED)
                + jobs.countByStatusIn(java.util.List.of(JobStatus.RUNNING, JobStatus.PAUSED));
    }

    @Transactional(readOnly = true)
    public long dueCount() { return jobs.findDue(java.util.List.of(JobStatus.PENDING, JobStatus.SCHEDULED), Instant.now()).size(); }
}
