package org.maibot.core.exceptions;

import org.maibot.sdk.exceptions.UnignorableException;

public class InstanceConstructException extends UnignorableException {
    public InstanceConstructException(String message, Object... args) {
        super(message, args);
    }
}
