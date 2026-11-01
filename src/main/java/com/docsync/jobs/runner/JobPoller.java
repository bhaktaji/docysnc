package com.docsync.jobs.runner;

import com.docsync.jobs.entity.AnalysisJob;
import com.docsync.jobs.service.JobExecutor;
import com.docsync.jobs.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class JobPoller {

    private static final Logger log = LoggerFactory.getLogger(JobPoller.class);

    private final JobService jobService;
    private final JobExecutor jobExecutor;

    public JobPoller(JobService jobService, JobExecutor jobExecutor) {
        this.jobService = jobService;
        this.jobExecutor = jobExecutor;
    }

    @Scheduled(fixedDelayString = "${docsync.jobs.poll-interval-seconds:5}000")
    public void poll() {
        jobService.pollNext().ifPresent(job -> execute(job));
    }

    private void execute(AnalysisJob job) {
        try {
            jobExecutor.execute(job);
            jobService.markCompleted(job.getId());
            log.info("Job {} completed", job.getId());
        } catch (RuntimeException ex) {
            log.warn("Job {} failed: {}", job.getId(), ex.getMessage());
            jobService.markFailed(job.getId(), ex.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverStaleJobs() {
        int recovered = jobService.requeueStaleJobs(
                Instant.now().minus(Duration.ofMinutes(5)));
        if (recovered > 0) {
            log.warn("Recovered {} stale jobs", recovered);
        }
    }
}
