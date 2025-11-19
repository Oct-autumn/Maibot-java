package org.maibot.sdk.mod;

/**
 * Mod基类，所有Mod均需继承此类
 *
 */
public abstract class Mod {
    /**
     * Mod启用时调用
     */
    public void onEnable() {
        // Do nothing by default
    }

    /**
     * Mod卸载时调用
     */
    public void onUnload() {
        // Do nothing by default
    }
}
