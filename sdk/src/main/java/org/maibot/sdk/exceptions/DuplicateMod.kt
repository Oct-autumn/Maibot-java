package org.maibot.sdk.exceptions

/**
 * Exception thrown when a duplicate mod is detected during the mod loading process.
 */
class DuplicateMod(format: String, vararg args: Any?) : FatalError(format, *args)
