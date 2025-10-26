package org.maibot.sdk.exceptions;

/**
 * Exception indicating that an error cannot be ignored and must be addressed.
 */
public class UnignorableException extends Exception {
    public UnignorableException(String message) {
        super(message);
    }

    public UnignorableException(String format, Object... args) {
        super(String.format(format, args));

        if (args.length > 0 && args[args.length - 1] instanceof Throwable) {
            this.initCause((Throwable) args[args.length - 1]);
        }
    }
}
