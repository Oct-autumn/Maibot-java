package org.maibot.sdk.exceptions

/**
 * Exception indicating a fatal error that cannot be recovered from.
 * 
 * 
 * This kind of exception should be used to signal critical failures
 * that will lead to the termination of the application or module.
 */
@Suppress("unused")
open class FatalError : RuntimeException {
    constructor(message: String) : super(message)

    constructor(cause: Throwable) : super(cause)

    constructor(format: String, vararg args: Any?) : super(String.format(format, *args)) {
        if (args.isNotEmpty() && args[args.size - 1] is Throwable) {
            this.initCause(args[args.size - 1] as Throwable?)
        }
    }
}
