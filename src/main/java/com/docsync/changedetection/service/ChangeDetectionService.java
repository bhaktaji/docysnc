package com.docsync.changedetection.service;

import com.docsync.changedetection.model.ChangedFile;
import com.docsync.repository.entity.Repository;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface ChangeDetectionService {

    List<ChangedFile> detectPushChanges(Repository repository, String baseCommit,
                                       String headCommit, JsonNode pushPayload);

    List<ChangedFile> detectPullRequestChanges(Repository repository, String baseCommit,
                                              String headCommit);
}
