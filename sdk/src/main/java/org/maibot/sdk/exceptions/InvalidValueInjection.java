package org.maibot.sdk.exceptions;

public class InvalidValueInjection extends UnignorableException {
    public InvalidValueInjection(String message, Object... args) {
        super(message, args);
    }
}
