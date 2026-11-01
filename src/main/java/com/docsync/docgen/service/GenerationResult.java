package com.docsync.docgen.service;

import com.docsync.docgen.entity.GeneratedDocumentationChange;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public record GenerationResult(UUID generationRunId, List<GeneratedDocumentationChange> changes,
                               int totalTokensUsed, Duration totalDuration) {
}
