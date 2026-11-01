package com.docsync.repository.clone;

import com.docsync.common.exception.DocSyncException;

/**
 * Raised when a git clone/fetch fails. Messages are sanitized and never contain tokens.
 */
public class GitOperationException extends DocSyncException {

    public GitOperationException(String message) {
        super("ERR_REPO_UNREACHABLE", message);
    }

    public GitOperationException(String message, Throwable cause) {
        super("ERR_REPO_UNREACHABLE", message, cause);
    }
}
