package org.maibot.core.exceptions;

import org.maibot.sdk.exceptions.FatalError;

/**
 * Exception thrown when a duplicate mod is detected during the mod loading process.
 */
public class DuplicateMod extends FatalError {
    public DuplicateMod(String format, Object... args) {
        super(format, args);
    }
}
