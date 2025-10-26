package org.maibot.sdk;

public abstract class Mod {
    /**
     * 模块加载时调用
     */
    public void onLoad() {
        // Do nothing by default
    }

    /**
     * 模块卸载时调用
     */
    public void onUnload() {
        // Do nothing by default
    }
}
