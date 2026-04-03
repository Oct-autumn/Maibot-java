package org.maibot.sdk.exceptions

class NotInitialized(format: String, vararg args: Any?) : UnignorableException(format, *args)
