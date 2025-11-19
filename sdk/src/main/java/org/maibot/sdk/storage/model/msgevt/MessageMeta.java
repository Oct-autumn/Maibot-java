package org.maibot.sdk.storage.model.msgevt;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.maibot.sdk.storage.domain.StreamType;

/**
 * 消息来源信息
 *
 * @param platform   平台标识
 * @param senderInfo 发送者信息
 * @param streamInfo 消息流信息
 */
public record MessageMeta(
  @JsonProperty("platform") String platform,
  @JsonProperty("sender_info") EntityInfo senderInfo,
  @JsonProperty("stream_info") StreamInfo streamInfo
) {
    /**
     * 实体信息
     *
     * @param platformId   实体在平台的ID
     * @param nickname     昵称
     * @param cardNickname 备注名(私聊)/群名片(群聊/群临时会话)
     */
    public record EntityInfo(
      @JsonProperty("platform_id") String platformId,
      @JsonProperty("nickname") String nickname,
      @JsonProperty("card_nickname") String cardNickname
    ) {
    }

    /**
     * 群组信息
     *
     * @param platformId 群组在平台的ID
     * @param groupName  群名称
     */
    public record GroupInfo(
      @JsonProperty("platform_id") String platformId,
      @JsonProperty("group_name") String groupName
    ) {
    }

    /**
     * 消息流信息
     *
     * @param privateInfo 私聊信息
     * @param groupInfo   群聊信息
     * @param streamType  消息类型
     */
    public record StreamInfo(
      @JsonProperty("private_info") EntityInfo privateInfo,
      @JsonProperty("group_info") GroupInfo groupInfo,
      @JsonProperty("stream_type") StreamType streamType
    ) {
    }
}
