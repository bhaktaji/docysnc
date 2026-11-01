package com.docsync.analysis.repository;

import com.docsync.analysis.entity.Finding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FindingRepository extends JpaRepository<Finding, UUID> {
    List<Finding> findAllByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    List<Finding> findAllByAnalysisId(UUID analysisId);
}
