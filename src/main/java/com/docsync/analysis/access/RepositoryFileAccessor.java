package com.docsync.analysis.access;

import com.docsync.repository.entity.Repository;
import com.docsync.changedetection.model.ChangedFile;

import java.util.List;

public interface RepositoryFileAccessor {

    List<String> listFiles(Repository repository, String commitSha);

    String readFile(Repository repository, String path, String commitSha);

    List<ChangedFile> getChangedFiles(Repository repository, String baseCommit, String headCommit);
}
