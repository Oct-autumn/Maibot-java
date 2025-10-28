package org.maibot.core.exceptions;

import org.maibot.sdk.exceptions.FatalError;

/**
 * Exception thrown when a circular dependency is detected among mods.
 */
public class CircularDependence extends FatalError {
    public CircularDependence(String format, Object... args) {
        super(format, args);
    }
}
