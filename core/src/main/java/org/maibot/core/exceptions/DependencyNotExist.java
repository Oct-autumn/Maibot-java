package org.maibot.core.exceptions;

import org.maibot.sdk.exceptions.FatalError;

/**
 * Exception thrown when a required dependency does not exist.
 */
public class DependencyNotExist extends FatalError {
    public DependencyNotExist(String format, Object... args) {
        super(format, args);
    }
}
