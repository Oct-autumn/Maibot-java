package org.maibot.sdk.exceptions;

/**
 * Exception indicating a fatal error that cannot be recovered from.
 * <p>
 * This kind of exception should be used to signal critical failures
 * that will lead to the termination of the application or module.
 */
public class FatalError extends RuntimeException {
    public FatalError(String message) {
        super(message);
    }

    public FatalError(Throwable cause) {
        super(cause);
    }

    public FatalError(String format, Object... args) {
        super(String.format(format, args));

        if (args.length > 0 && args[args.length - 1] instanceof Throwable) {
            this.initCause((Throwable) args[args.length - 1]);
        }
    }
}
