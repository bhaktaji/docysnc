package com.docsync.identity.exception;

import com.docsync.common.exception.DocSyncException;

/**
 * Thrown when supplied credentials are invalid. Deliberately does not reveal
 * whether the email exists.
 */
public class InvalidCredentialsException extends DocSyncException {

    public InvalidCredentialsException() {
        super("ERR_AUTH_INVALID", "Invalid email or password");
    }
}
