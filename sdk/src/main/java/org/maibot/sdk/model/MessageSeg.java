package org.maibot.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息段
 */
@Getter
@AllArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class MessageSeg {
    /// 消息段类型，常见类型：
    /// - <code>list</code>: 消息段列表
    /// - <code>text</code>: 文本消息段
    /// - <code>image</code>: 图片消息段
    /// - <code>emoji</code>: 表情包（本质还是图片）消息段
    /// - <code>voice</code>: 语音消息段
    /// - <code>at</code>: At消息段
    private final String type;
    /// 消息段数据，具体结构根据type不同而不同
    /// - 当type为"list"时，data为List<MessageSeg>
    /// - 当type为"text"时，data为String
    /// - 当type为"image"/"emoji"时，data为图片Base64编码字符串
    /// - 当type为"voice"时，data为语音Base64编码字符串
    /// - 当type为"at"时，data为被At用户的平台ID字符串
    private final Object data;
}
