package org.maibot.sdk.model.msgevt;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.maibot.sdk.SeqGenerator;
import org.maibot.sdk.db.DatabaseService;
import org.maibot.sdk.db.dao.InteractionEntity;
import org.maibot.sdk.db.dao.InteractionStream;
import org.maibot.sdk.db.dao.Message;

/**
 * 抽象消息事件接口，定义了将消息转化为提示词字符串和数据库存储对象的方法
 * <p>
 * 若想要实现消息事件，请实现此接口。同时，为确保消息能被顺利地从数据库中取出，
 * 请实现并注册相应的工厂类。
 *
 * @author OctAutumn
 */
@SuppressWarnings("unused")
public abstract class AbstractMessageEvent {
    /// 消息来源信息
    @JsonProperty("message_meta")
    protected final MessageMeta           messageMeta;
    /// 消息时间戳
    @JsonProperty("timestamp")
    protected final Long                  timestamp;
    /// 消息序列号（可用于排序）
    @JsonProperty("sequence")
    protected final SeqGenerator.Sequence sequence;

    /**
     * @throws NullPointerException 若 `platform`、`messageType` 或 `timestamp` 为空，
     *                              或 `senderInfo` 和 `groupInfo` 同时为空
     */
    public AbstractMessageEvent(
      String platform,
      MessageMeta.EntityInfo senderInfo,
      MessageMeta.StreamInfo streamInfo,
      Long timestamp,
      SeqGenerator.Sequence sequence
    ) {
        this.messageMeta = new MessageMeta(platform, senderInfo, streamInfo);
        this.timestamp = timestamp;
        this.sequence = sequence;
    }

    /**
     * 获取消息来源信息
     *
     * @return 消息来源信息对象
     */
    public MessageMeta messageSource() {
        return messageMeta;
    }

    /**
     * 获取消息时间戳
     *
     * @return 消息时间戳
     */
    public Long timestamp() {
        return timestamp;
    }

    /**
     * 获取消息序列号
     *
     * @return 消息序列号
     */
    public SeqGenerator.Sequence sequence() {
        return sequence;
    }

    /**
     * 将消息转化为提示词字符串
     */
    public abstract String toPromptString();

    /**
     * 将消息的额外字段转化为数据库存储对象
     *
     * @return 数据库消息对象
     */
    public Message toDatabaseObject(DatabaseService dbService) {
        var message = new Message();

        dbService.exec(em -> {
            // 两个查询：
            // 1. 查询 interaction_entity 表，获取发送者实体的 ID
            // 2. 将 interaction_stream 表和 interaction_entity、interaction_group 表关联起来，通过消息类型辨别要
            //     关联Entity的互动流还是Group的互动流，获取互动流的 ID

            // 查询发送者实体
            var senderEntity = em.createQuery(
                                   "select entity from InteractionEntity entity"
                                     + " where entity.platform = :platform and entity.platformUserId = :platformId",
                                   InteractionEntity.class
                                 )
                                 .setParameter("platform", this.messageMeta.platform())
                                 .setParameter("platformId", this.messageMeta.senderInfo().platformId())
                                 .getSingleResult();
            message.setSender(senderEntity);

            // 查询互动流
            var streamInfo = this.messageMeta.streamInfo();
            switch (streamInfo.streamType()) {
                case PRIVATE -> {
                    var stream =
                      em.createQuery(
                          "select stream from InteractionStream stream"
                            + " join stream.entity entity"
                            + " where entity.platform = :platform and entity.platformUserId = :platformUserId",
                          InteractionStream.class
                        )
                        .setParameter("platform", this.messageMeta.platform())
                        .setParameter("platformUserId", streamInfo.privateInfo().platformId())
                        .getSingleResult();
                    message.setStream(stream);
                }
                case GROUP -> {
                    var stream =
                      em.createQuery(
                          "select stream from InteractionStream stream"
                            + " join stream.group group"
                            + " where group.platform = :platform and group.platformGroupId = :platformGroupId",
                          InteractionStream.class
                        )
                        .setParameter("platform", this.messageMeta.platform())
                        .setParameter("platformGroupId", streamInfo.groupInfo().platformId())
                        .getSingleResult();
                }
                case GROUP_TEMP -> {
                    // 暂不做支持，预期行为同 PRIVATE
                }
            }
        });

        return message;
    }

    @Override
    public String toString() {
        return String.format(
          "AbstractMessageEvent[messageSource=%s, timestamp=%d, sequence=%s]",
          messageMeta,
          timestamp,
          sequence
        );
    }
}
