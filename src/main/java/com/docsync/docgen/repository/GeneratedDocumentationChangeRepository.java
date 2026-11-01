package com.docsync.docgen.repository;

import com.docsync.docgen.entity.GeneratedDocumentationChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GeneratedDocumentationChangeRepository extends JpaRepository<GeneratedDocumentationChange, UUID> {
    List<GeneratedDocumentationChange> findAllByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    List<GeneratedDocumentationChange> findAllByGenerationRunId(UUID generationRunId);

    int countByRepositoryIdAndFilePath(UUID repositoryId, String filePath);
}
