package org.maibot.sdk.storage.model.msgevt;

import org.maibot.sdk.storage.db.dao.GroupMember;
import org.maibot.sdk.storage.db.dao.Message;
import org.maibot.sdk.storage.domain.StreamType;

import java.util.Objects;

public class MessageMetaFactory {
    private String platform;

    private MessageMeta.EntityInfo senderInfo;

    private MessageMeta.StreamInfo streamInfo;

    public MessageMetaFactory setPlatform(String platform) {
        this.platform = platform;
        return this;
    }

    public MessageMetaFactory setSenderInfo(MessageMeta.EntityInfo senderInfo) {
        this.senderInfo = senderInfo;
        return this;
    }

    public MessageMetaFactory setStreamInfo(MessageMeta.StreamInfo streamInfo) {
        this.streamInfo = streamInfo;
        return this;
    }

    public String getPlatform() {
        return platform;
    }

    public MessageMeta.EntityInfo getSenderInfo() {
        return senderInfo;
    }

    public MessageMeta.StreamInfo getStreamInfo() {
        return streamInfo;
    }

    /**
     * 从数据库提取 MessageMeta 信息。
     * <p>
     * 建议在EntityManager的上下文中调用此方法，以确保延迟加载的属性能够正确获取。<br>
     * 若在EntityManager上下文之外调用，则需要提前加载相关属性，避免出现LazyInitializationException异常。
     *
     * @param message 数据库消息对象
     * @return MessageMetaFactory 实例
     */
    public MessageMetaFactory fromDatabase(Message message) {
        var sender = message.getSender();
        var stream = message.getStream();

        this.platform = sender.getPlatform();

        switch (stream.getType()) {
            case PRIVATE -> {
                var streamEntity = stream.getEntity();
                this.streamInfo = new MessageMeta.StreamInfo(
                  new MessageMeta.EntityInfo(
                    streamEntity.getPlatformUserId(),
                    streamEntity.getNickname(),
                    null
                  ), null, StreamType.PRIVATE
                );
                this.senderInfo = new MessageMeta.EntityInfo(sender.getPlatformUserId(), sender.getNickname(), null);
            }
            case GROUP -> {
                var streamEntity = stream.getEntity();
                var streamGroup = stream.getGroup();
                this.streamInfo = new MessageMeta.StreamInfo(
                  new MessageMeta.EntityInfo(
                    streamEntity.getPlatformUserId(),
                    streamEntity.getNickname(),
                    streamEntity.getGroupMembers()
                      .stream()
                      .filter(gm1 -> gm1.getGroupId().equals(streamGroup.getId()))
                      .map(GroupMember::getCardName)
                      .findFirst()
                      .orElse(null)
                  ),
                  new MessageMeta.GroupInfo(streamGroup.getPlatformGroupId(), streamGroup.getGroupName()),
                  StreamType.PRIVATE
                );
                this.senderInfo = new MessageMeta.EntityInfo(
                  sender.getPlatformUserId(),
                  sender.getNickname(),
                  sender.getGroupMembers()
                    .stream()
                    .filter(gm -> gm.getGroupId().equals(streamGroup.getId()))
                    .map(GroupMember::getCardName)
                    .findFirst()
                    .orElse(null)
                );
            }
            case GROUP_TEMP -> {
                // 暂不做支持，预期行为同 PRIVATE
            }
            case GROUP_ANONYMOUS -> {
                // 不计划支持
            }
        }

        return this;
    }

    public MessageMeta build() {
        Objects.requireNonNull(platform, "platform must not be null");
        Objects.requireNonNull(senderInfo, "senderInfo must not be null");
        Objects.requireNonNull(streamInfo, "streamInfo must not be null");

        return new MessageMeta(platform, senderInfo, streamInfo);
    }
}

