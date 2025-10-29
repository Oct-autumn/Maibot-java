package org.maibot.sdk.ioc;

/**
 * 可定义后置初始化方法的组件接口
 */
public interface InitializableComponent {
    void postConstruct();
}
