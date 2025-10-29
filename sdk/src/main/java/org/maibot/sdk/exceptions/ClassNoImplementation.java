package org.maibot.sdk.exceptions;

/**
 * 当请求的类没有任何实现类时抛出此异常</br>
 * When no implementation class is found for the requested class, this exception is thrown.
 */
public class ClassNoImplementation extends UnignorableException {
    public ClassNoImplementation(String message, Object... args) {
        super(message, args);
    }
}
