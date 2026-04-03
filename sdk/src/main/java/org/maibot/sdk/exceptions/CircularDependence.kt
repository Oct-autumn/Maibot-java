package org.maibot.sdk.exceptions;

/**
 * Exception thrown when a circular dependency is detected among mods.
 */
public class CircularDependence extends FatalError {
    public CircularDependence(String format, Object... args) {
        super(format, args);
    }
}
