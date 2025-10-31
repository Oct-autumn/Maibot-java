package org.maibot.sdk.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class MessageEventFactory {
    /// 平台标识
    @Getter
    @Setter
    private String                   platform    = null;
    /// 发送者信息
    @Getter
    @Setter
    private EntityInfo               senderInfo  = null;
    /// 群组信息
    @Getter
    @Setter
    private GroupInfo                groupInfo   = null;
    /// 消息类型
    @Getter
    @Setter
    private MessageEvent.MessageType messageType = null;
    /// 消息时间戳
    @Getter
    @Setter
    private Long                     timestamp   = null;
    /// 消息内容
    @Getter
    @Setter
    private MessageSeg               message     = null;
    /// 额外字段的键值对
    private Map<String, String>      extra       = new HashMap<>();

    public void putExtra(String key, String value) {
        this.extra.put(key, value);
    }

    public String getExtra(String key) {
        return this.extra.get(key);
    }

    public void removeExtra(String key) {
        if (this.extra != null) {
            this.extra.remove(key);
        }
    }

    public MessageEvent build() {
        return new MessageEvent(
          platform,
          senderInfo,
          groupInfo,
          messageType,
          timestamp,
          message,
          extra
        );
    }
}
