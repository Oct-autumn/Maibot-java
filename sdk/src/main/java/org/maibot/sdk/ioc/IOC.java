package org.maibot.sdk.ioc;

/**
 * IOC 容器接口，提供获取实例的方法
 */
public interface IOC {

    <T> T get(Class<T> interfaceOrClass, String name);

    <T> T get(Class<T> interfaceOrClass);
}
