package org.maibot.sdk.exceptions

/**
 * 当请求的类没有任何实现类时抛出此异常
 * When no implementation class is found for the requested class, this exception is thrown.
 */
class ClassNoImplementation(message: String, vararg args: Any?) : UnignorableException(message, *args)
