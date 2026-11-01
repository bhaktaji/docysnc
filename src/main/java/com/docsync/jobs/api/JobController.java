package com.docsync.jobs.api;

import com.docsync.identity.security.CurrentUser;
import com.docsync.jobs.dto.JobDto;
import com.docsync.jobs.entity.AnalysisJob;
import com.docsync.jobs.entity.JobStatus;
import com.docsync.jobs.entity.JobType;
import com.docsync.jobs.service.JobService;
import com.docsync.organization.dto.OrganizationDto;
import com.docsync.organization.service.OrganizationAuthorizationService;
import com.docsync.organization.service.OrganizationService;
import com.docsync.repository.entity.Repository;
import com.docsync.repository.service.RepositoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class JobController {

    private final JobService jobService;
    private final RepositoryService repositoryService;
    private final OrganizationService organizationService;
    private final OrganizationAuthorizationService authorizationService;
    private final CurrentUser currentUser;

    public JobController(JobService jobService, RepositoryService repositoryService,
                         OrganizationService organizationService,
                         OrganizationAuthorizationService authorizationService, CurrentUser currentUser) {
        this.jobService = jobService;
        this.repositoryService = repositoryService;
        this.organizationService = organizationService;
        this.authorizationService = authorizationService;
        this.currentUser = currentUser;
    }

    @GetMapping("/jobs/{jobId}")
    public JobDto getJob(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID jobId) {
        AnalysisJob job = jobService.getJob(jobId)
                .orElseThrow(() -> new com.docsync.common.exception.ResourceNotFoundException("Job", jobId.toString()));
        Repository repository = repositoryService.findRepositoryById(job.getRepositoryId())
                .orElseThrow(() -> new com.docsync.common.exception.ResourceNotFoundException("Repository", job.getRepositoryId().toString()));
        authorizationService.requireMembership(currentUser.id(jwt), repository.getOrganizationId());
        return JobDto.from(job);
    }

    @GetMapping("/jobs")
    public List<JobDto> listAllJobs(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(required = false) UUID organizationId,
                                    @RequestParam(required = false) UUID repositoryId,
                                    @RequestParam(required = false) JobType type,
                                    @RequestParam(required = false) JobStatus status) {
        UUID actorId = currentUser.id(jwt);
        List<UUID> organizationIds;
        if (organizationId != null) {
            authorizationService.requireMembership(actorId, organizationId);
            organizationIds = List.of(organizationId);
        } else {
            organizationIds = organizationService.getUserOrganizations(actorId).stream()
                    .map(OrganizationDto::id).toList();
        }

        List<JobDto> jobs = new ArrayList<>();
        for (UUID orgId : organizationIds) {
            // RepositoryService requires actor for tenant scoping; reuse existing method.
            List<Repository> repositories = repositoryService.listRepositories(actorId, orgId, null)
                    .stream()
                    .map(dto -> repositoryService.findRepositoryById(dto.id()).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            for (Repository repository : repositories) {
                if (repositoryId != null && !repository.getId().equals(repositoryId)) {
                    continue;
                }
                for (AnalysisJob job : jobService.listJobsForRepository(repository.getId())) {
                    if (type != null && job.getJobType() != type) {
                        continue;
                    }
                    if (status != null && job.getStatus() != status) {
                        continue;
                    }
                    jobs.add(JobDto.from(job));
                }
            }
        }
        return jobs;
    }

    @GetMapping("/organizations/{organizationId}/repositories/{repositoryId}/jobs")
    public List<JobDto> listJobs(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID organizationId,
                                 @PathVariable UUID repositoryId) {
        authorizationService.requireMembership(currentUser.id(jwt), organizationId);
        Repository repository = repositoryService.findRepositoryById(repositoryId)
                .orElseThrow(() -> new com.docsync.common.exception.ResourceNotFoundException("Repository", repositoryId.toString()));
        if (!repository.getOrganizationId().equals(organizationId)) {
            throw new com.docsync.common.exception.ResourceNotFoundException("Repository", repositoryId.toString());
        }
        return jobService.listJobsForRepository(repositoryId).stream().map(JobDto::from).toList();
    }
}
