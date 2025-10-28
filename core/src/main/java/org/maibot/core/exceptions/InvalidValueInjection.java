package org.maibot.core.exceptions;

import org.maibot.sdk.exceptions.UnignorableException;

public class InvalidValueInjection extends UnignorableException {
    public InvalidValueInjection(String message, Object... args) {
        super(message, args);
    }
}
