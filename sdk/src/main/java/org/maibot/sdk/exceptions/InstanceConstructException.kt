package org.maibot.sdk.exceptions;

public class InstanceConstructException extends UnignorableException {
    public InstanceConstructException(String message, Object... args) {
        super(message, args);
    }
}
