package org.maibot.sdk.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.List;
import java.util.Map;

/**
 * 消息事件
 *
 * @param platform    平台标识
 * @param senderInfo  发送者信息
 * @param groupInfo   群组信息
 * @param messageType 消息类型
 * @param timestamp   消息时间戳
 * @param messageSeg  消息内容
 * @param extra       额外字段的键值对
 */
public record MessageEvent(
  @JsonProperty(value = "platform", required = true) String platform,
  @JsonProperty(value = "sender_info") EntityInfo senderInfo,
  @JsonProperty(value = "group_info") GroupInfo groupInfo,
  @JsonProperty(value = "message_type", required = true) MessageType messageType,
  @JsonProperty(value = "timestamp", required = true) Long timestamp,
  @JsonProperty(value = "message_seg") MessageSeg messageSeg,
  @JsonProperty(value = "extra") Map<String, String> extra
) {
    /**
     * @param platformId   实体在平台的ID
     * @param nickname     昵称
     * @param cardNickname 备注名(私聊)/群名片(群聊/群临时会话)
     */
    public record EntityInfo(
      @JsonProperty(value = "platform_id", required = true) String platformId,
      @JsonProperty(value = "nickname") String nickname,
      @JsonProperty(value = "card_nickname") String cardNickname
    ) {
    }

    /**
     * @param platformId 群组在平台的ID
     * @param groupName  群名称
     */
    public record GroupInfo(
      @JsonProperty(value = "platform_id", required = true) String platformId,
      @JsonProperty(value = "group_name") String groupName
    ) {
    }

    /**
     * @param type 消息段类型，常见类型：
     *             <ul>
     *             <li> <code>list</code>: 消息段列表
     *             <li> <code>text</code>: 文本消息段
     *             <li> <code>image</code>: 图片消息段
     *             <li> <code>emoji</code>: 表情包（本质还是图片）消息段
     *             <li> <code>voice</code>: 语音消息段
     *             <li> <code>at</code>: At消息段
     *             </ul>
     * @param data 消息段数据，具体结构根据type不同而不同
     *             <ul>
     *             <li> 当type为<code>list</code>时，data为List<MessageSeg>
     *             <li> 当type为<code>text</code>时，data为String
     *             <li> 当type为<code>image</code>/<code>emoji</code>时，data为图片Base64编码字符串
     *             <li> 当type为<code>voice</code>时，data为语音Base64编码字符串
     *             <li> 当type为<code>at</code>时，data为被At用户的平台ID字符串
     *             </ul>
     */
    public record MessageSeg(
      @JsonProperty(value = "type") String type,
      Object data
    ) {
        public boolean isArray() {
            return "array".equals(type);
        }

        public boolean isText() {
            return "text".equals(type);
        }

        public boolean isImage() {
            return "image".equals(type);
        }

        public boolean isEmoji() {
            return "emoji".equals(type);
        }

        public boolean isVoice() {
            return "voice".equals(type);
        }

        public boolean isAt() {
            return "at".equals(type);
        }

        public MessageSeg[] asArray() {
            if (!isArray()) {
                throw new IllegalStateException("MessageSeg is not of type 'list'");
            }
            assert data instanceof MessageSeg[];
            return (MessageSeg[]) data;
        }

        public String asText() {
            if (!isText()) {
                throw new IllegalStateException("MessageSeg is not of type 'text'");
            }
            return (String) data;
        }

        public String asImage() {
            if (!isImage()) {
                throw new IllegalStateException("MessageSeg is not of type 'image'");
            }
            return (String) data;
        }

        public String asEmoji() {
            if (!isEmoji()) {
                throw new IllegalStateException("MessageSeg is not of type 'emoji'");
            }
            return (String) data;
        }

        public String asVoice() {
            if (!isVoice()) {
                throw new IllegalStateException("MessageSeg is not of type 'voice'");
            }
            return (String) data;
        }

        public String asAt() {
            if (!isAt()) {
                throw new IllegalStateException("MessageSeg is not of type 'at'");
            }
            return (String) data;
        }
    }

    public enum MessageType {
        /// 私聊
        PRIVATE("PRIVATE"),
        /// 群聊
        GROUP("GROUP"),
        /// 群临时会话
        GROUP_TEMP("GROUP_TEMP");

        private final String value;

        @JsonCreator
        MessageType(String value) {
            this.value = value;
        }

        @JsonSerialize
        public String getValue() {
            return value;
        }
    }
}
