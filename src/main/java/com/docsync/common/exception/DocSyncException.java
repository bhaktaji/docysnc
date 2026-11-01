package com.docsync.common.exception;

/**
 * Base exception for all DocSync application exceptions.
 */
public abstract class DocSyncException extends RuntimeException {

    private final String errorCode;

    protected DocSyncException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected DocSyncException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
