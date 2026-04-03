package org.maibot.sdk.exceptions;

public class NotInitialized extends UnignorableException {
    public NotInitialized(String format, Object... args) {
        super(format, args);
    }
}
