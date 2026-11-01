package com.docsync.analysis.repository;

import com.docsync.analysis.entity.DocumentationCodeReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentationCodeReferenceRepository extends JpaRepository<DocumentationCodeReference, UUID> {
    List<DocumentationCodeReference> findAllByRepositoryIdAndPageId(UUID repositoryId, UUID pageId);

    void deleteAllByRepositoryIdAndPageId(UUID repositoryId, UUID pageId);
}
