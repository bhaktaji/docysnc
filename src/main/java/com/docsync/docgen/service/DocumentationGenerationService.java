package com.docsync.docgen.service;

import com.docsync.docgen.entity.GeneratedDocumentationChange;

import java.util.List;
import java.util.UUID;

public interface DocumentationGenerationService {

    GenerationResult generateUpdates(UUID repositoryId, UUID analysisId);

    GeneratedDocumentationChange getChange(UUID changeId);

    List<GeneratedDocumentationChange> getChangesForRepository(UUID repositoryId);

    void approveChange(UUID changeId);

    void rejectChange(UUID changeId);

    GeneratedDocumentationChange regenerateChange(UUID changeId);
}
