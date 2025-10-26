package org.maibot.core.modloader.exceptions;

import org.maibot.sdk.exceptions.FatalError;

/**
 * Exception thrown when a circular dependency is detected among mods.
 */
public class CircularDependence extends FatalError {
    public CircularDependence(String message) {
        super(message);
    }

    public CircularDependence(String format, Object... args) {
        super(format, args);
    }
}
