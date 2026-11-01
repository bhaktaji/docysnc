package com.docsync.analysis.repository;

import com.docsync.analysis.entity.CodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CodeEntityRepository extends JpaRepository<CodeEntity, UUID> {
    List<CodeEntity> findAllByRepositoryIdAndCommitSha(UUID repositoryId, String commitSha);

    List<CodeEntity> findAllByRepositoryIdAndCommitShaAndFilePath(UUID repositoryId, String commitSha, String filePath);

    void deleteAllByRepositoryIdAndCommitShaAndFilePath(UUID repositoryId, String commitSha, String filePath);
}
