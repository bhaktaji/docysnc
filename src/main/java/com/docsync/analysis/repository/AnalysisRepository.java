package com.docsync.analysis.repository;

import com.docsync.analysis.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {
    List<Analysis> findAllByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);
}
