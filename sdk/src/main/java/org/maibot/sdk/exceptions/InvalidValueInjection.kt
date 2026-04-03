package org.maibot.sdk.exceptions

class InvalidValueInjection(message: String, vararg args: Any?) : UnignorableException(message, *args)
