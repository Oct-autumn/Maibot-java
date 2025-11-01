package org.maibot.core.ioc;

import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.ioc.IOC;

/**
 * IOC 容器包装类，为Mod提供底层的获取实例的方法
 * <p>
 * 不要在Core中直接使用此类，应通过@AutoInject或Instance类获取实例
 */
@Component
public class InstanceImpl implements IOC {
    @Override
    public <T> T get(Class<T> interfaceOrClass, String name) {
        return Instance.get(interfaceOrClass, name);
    }

    @Override
    public <T> T get(Class<T> interfaceOrClass) {
        return Instance.get(interfaceOrClass);
    }
}
