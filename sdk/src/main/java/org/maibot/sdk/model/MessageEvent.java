package org.maibot.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * 消息事件
 */
@Getter
@AllArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class MessageEvent {
    /// 平台标识
    private final String              platform;
    /// 发送者信息
    private final EntityInfo          senderInfo;
    /// 群组信息
    private final GroupInfo           groupInfo;
    /// 消息类型
    private final MessageType         messageType;
    /// 消息时间戳
    private final Long                timestamp;
    /// 消息内容
    private final MessageSeg          message;
    /// 额外字段的键值对
    private final Map<String, String> extra;

    public enum MessageType {
        /// 私聊
        PRIVATE,
        /// 群聊
        GROUP,
        /// 群临时会话
        GROUP_TEMP
    }
}
