package org.maibot.sdk.exceptions

/**
 * Exception thrown when a required dependency does not exist.
 */
class DependencyNotExist(format: String, vararg args: Any?) : FatalError(format, *args)
