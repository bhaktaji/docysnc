package com.docsync.health;

import com.docsync.jobs.entity.JobStatus;
import com.docsync.jobs.repository.AnalysisJobRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("jobQueue")
public class JobQueueHealthIndicator implements HealthIndicator {

    private final AnalysisJobRepository jobRepository;

    public JobQueueHealthIndicator(AnalysisJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public Health health() {
        long queued = jobRepository.countByStatus(JobStatus.QUEUED);
        long running = jobRepository.countByStatus(JobStatus.RUNNING);
        long failed = jobRepository.countByStatus(JobStatus.FAILED);
        return Health.up()
                .withDetail("queued", queued)
                .withDetail("running", running)
                .withDetail("failed", failed)
                .build();
    }
}
