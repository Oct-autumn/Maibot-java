package org.maibot.core.exceptions;

import org.maibot.sdk.exceptions.IgnorableException;

public class DbOperationException extends IgnorableException {
    public DbOperationException(String message) {
        super(message);
    }

    public DbOperationException(String message, Object... args) {
        super(message, args);
    }
}
