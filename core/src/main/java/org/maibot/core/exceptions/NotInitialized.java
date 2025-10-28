package org.maibot.core.exceptions;

import org.maibot.sdk.exceptions.UnignorableException;

public class NotInitialized extends UnignorableException {
    public NotInitialized(String format, Object... args) {
        super(format, args);
    }
}
