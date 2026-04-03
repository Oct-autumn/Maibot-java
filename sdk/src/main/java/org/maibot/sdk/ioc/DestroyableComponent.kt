package org.maibot.sdk.ioc

/**
 * 可以在销毁前执行清理操作的组件接口
 * 
 * 
 * 实现此接口的组件将在被销毁前调用 [.preDestroy] 方法（仅对单例组件有效）
 */
interface DestroyableComponent {
    fun preDestroy()
}
