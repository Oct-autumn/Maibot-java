package org.maibot.sdk.mod

/**
 * Mod基类，所有Mod均需继承此类
 * 
 */
abstract class Mod {
    /**
     * Mod启用时调用
     */
    @Suppress("EmptyMethod")
    open fun onEnable() {
        // Do nothing by default
    }

    /**
     * Mod卸载时调用
     */
    @Suppress("EmptyMethod")
    open fun onUnload() {
        // Do nothing by default
    }
}
