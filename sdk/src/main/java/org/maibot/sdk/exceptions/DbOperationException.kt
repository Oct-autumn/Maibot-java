package org.maibot.sdk.exceptions

class DbOperationException(message: String, vararg args: Any?) : IgnorableException(message, *args)
