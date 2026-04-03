package org.maibot.sdk.exceptions

/**
 * Exception indicating that an error cannot be ignored and must be addressed.
 */
@Suppress("unused")
open class UnignorableException : RuntimeException {
    constructor(message: String) : super(message)

    constructor(cause: Throwable) : super(cause)

    constructor(format: String, vararg args: Any?) : super(String.format(format, *args)) {
        if (args.isNotEmpty() && args[args.size - 1] is Throwable) {
            this.initCause(args[args.size - 1] as Throwable?)
        }
    }
}
