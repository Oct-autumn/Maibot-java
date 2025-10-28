package org.maibot.core.exceptions;

import org.maibot.sdk.exceptions.UnignorableException;

public class InvalidConfigPath extends UnignorableException {
    public InvalidConfigPath(String message, Object... args) {
        super(message, args);
    }
}
