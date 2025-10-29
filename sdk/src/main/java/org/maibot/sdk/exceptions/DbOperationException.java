package org.maibot.sdk.exceptions;

public class DbOperationException extends IgnorableException {
    public DbOperationException(String message) {
        super(message);
    }

    public DbOperationException(String message, Object... args) {
        super(message, args);
    }
}
