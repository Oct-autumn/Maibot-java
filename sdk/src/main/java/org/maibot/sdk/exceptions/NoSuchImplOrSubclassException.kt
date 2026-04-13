package org.maibot.sdk.exceptions

/**
 * 当请求的类没有任何实现对应的实现类/子类时抛出此异常
 * When no implementation or subclass is found for a requested class, this exception is thrown.
 */
class NoSuchImplOrSubclassException(message: String, vararg args: Any?) : UnignorableException(message, *args)
