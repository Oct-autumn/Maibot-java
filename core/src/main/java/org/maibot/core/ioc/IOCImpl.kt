package org.maibot.core.ioc

import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.IOC

/**
 * IOC 容器包装类，为Mod提供底层的获取实例的方法
 * 
 * 
 * 不要在Core中直接使用此类，应通过@AutoInject或Instance类获取实例
 */
@Component
class IOCImpl : IOC {
    override fun <T> get(interfaceOrClass: Class<T>, name: String): T {
        return Instance.get(interfaceOrClass, name)
    }

    override fun <T> get(interfaceOrClass: Class<T>): T {
        return Instance.get(interfaceOrClass)
    }
}
