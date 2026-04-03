package org.maibot.sdk.ioc

/**
 * IOC 容器接口，提供获取实例的方法
 */
interface IOC {
    fun <T> get(interfaceOrClass: Class<T>, name: String): T

    fun <T> get(interfaceOrClass: Class<T>): T
}
