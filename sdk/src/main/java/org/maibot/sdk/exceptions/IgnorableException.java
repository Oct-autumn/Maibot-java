package org.maibot.sdk.exceptions;

/**
 * Exception indicating that an error can be safely ignored.
 */
public class IgnorableException extends RuntimeException {
    public IgnorableException(String message) {
        super(message);
    }

    public IgnorableException(Throwable cause) {
        super(cause);
    }

    public IgnorableException(String format, Object... args) {
        super(String.format(format, args));

        if (args.length > 0 && args[args.length - 1] instanceof Throwable) {
            this.initCause((Throwable) args[args.length - 1]);
        }
    }
}
