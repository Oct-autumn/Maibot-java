package org.maibot.sdk.ioc;

/**
 * 可以在销毁前执行清理操作的组件接口
 */
public interface DestroyableComponent {
    void preDestroy();
}
