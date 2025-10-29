package org.maibot.sdk.exceptions;

public class InvalidConfigPath extends UnignorableException {
    public InvalidConfigPath(String message, Object... args) {
        super(message, args);
    }
}
