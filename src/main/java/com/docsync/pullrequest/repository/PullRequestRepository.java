package com.docsync.pullrequest.repository;

import com.docsync.pullrequest.entity.PullRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PullRequestRepository extends JpaRepository<PullRequest, UUID> {

    Optional<PullRequest> findByRepositoryIdAndPrNumber(UUID repositoryId, int prNumber);
}
