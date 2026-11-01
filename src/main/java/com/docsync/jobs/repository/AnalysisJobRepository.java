package com.docsync.jobs.repository;

import com.docsync.jobs.entity.AnalysisJob;
import com.docsync.jobs.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, UUID> {

    @Query(value = """
            SELECT * FROM analysis_jobs
            WHERE status = 'QUEUED'
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY priority DESC, created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<AnalysisJob> findNextQueuedJob(@Param("now") Instant now);

    List<AnalysisJob> findAllByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    long countByStatus(com.docsync.jobs.entity.JobStatus status);

    Optional<AnalysisJob> findFirstByRepositoryIdAndJobTypeAndTriggerCommitAndPrNumberOrderByCreatedAtDesc(
            UUID repositoryId, com.docsync.jobs.entity.JobType jobType, String triggerCommit, Integer prNumber);

    List<AnalysisJob> findAllByStatusAndLockedAtBefore(JobStatus status, Instant threshold);
}
