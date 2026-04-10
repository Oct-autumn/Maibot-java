package org.maibot.sdk.exceptions

class ModelRequestFailed(format: String, vararg args: Any?) : UnignorableException(format, *args)
