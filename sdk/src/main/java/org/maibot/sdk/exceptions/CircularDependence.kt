package org.maibot.sdk.exceptions

/**
 * Exception thrown when a circular dependency is detected among mods.
 */
class CircularDependence(format: String, vararg args: Any?) : FatalError(format, *args)
