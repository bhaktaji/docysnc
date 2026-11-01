package com.docsync.analysis.service;

import com.docsync.analysis.entity.Analysis;
import com.docsync.analysis.entity.Finding;

import java.util.List;
import java.util.UUID;

public interface RepositoryAnalysisService {

    Analysis analyzeRepository(UUID repositoryId, String commitSha, com.docsync.analysis.entity.AnalysisScope scope, UUID jobId);

    Analysis analyzeChangedFiles(UUID repositoryId, String baseCommit, String headCommit, UUID jobId);

    List<Analysis> listAnalyses(UUID repositoryId);

    Analysis getAnalysis(UUID analysisId);

    List<Finding> getFindings(UUID repositoryId);
}
