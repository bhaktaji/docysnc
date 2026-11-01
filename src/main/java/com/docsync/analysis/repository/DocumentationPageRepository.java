package com.docsync.analysis.repository;

import com.docsync.analysis.entity.DocumentationPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentationPageRepository extends JpaRepository<DocumentationPage, UUID> {
    List<DocumentationPage> findAllByRepositoryId(UUID repositoryId);

    Optional<DocumentationPage> findByRepositoryIdAndPath(UUID repositoryId, String path);
}
