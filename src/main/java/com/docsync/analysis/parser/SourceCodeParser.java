package com.docsync.analysis.parser;

import com.docsync.analysis.entity.CodeEntity;
import com.docsync.analysis.entity.CodeEntityType;

import java.util.UUID;

import java.util.List;

public interface SourceCodeParser {

    boolean supports(String filePath);

    List<CodeEntity> parse(String filePath, String content, UUID repositoryId, UUID analysisId, String commitSha);
}
