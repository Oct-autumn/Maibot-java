package org.maibot.sdk.mod;

public abstract class Mod {
    /**
     * Mod加载时调用
     */
    public void onLoad() {
        // Do nothing by default
    }

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
