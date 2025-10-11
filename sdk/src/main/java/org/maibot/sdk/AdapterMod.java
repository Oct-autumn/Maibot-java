package org.maibot.sdk;

/**
 * 协议适配器
 * 用于提供外部协议支持
 */
public interface AdapterMod {
    /**
     *
     */

    void eventReceived(String event, Object data);

}
