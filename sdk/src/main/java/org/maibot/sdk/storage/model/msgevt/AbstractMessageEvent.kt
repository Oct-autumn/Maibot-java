package org.maibot.sdk.storage.model.msgevt;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.EntityManager;
import org.maibot.sdk.SNoGenerator;
import org.maibot.sdk.manager.InteractionEntityManager;
import org.maibot.sdk.manager.InteractionGroupManager;
import org.maibot.sdk.manager.InteractionStreamManager;
import org.maibot.sdk.storage.db.dao.InteractionStream;
import org.maibot.sdk.storage.db.dao.Message;
import org.maibot.sdk.storage.domain.StreamType;
import org.maibot.sdk.util.UnwrapUtils;
import tools.jackson.databind.ObjectMapper;

import static java.util.Objects.requireNonNull;

/**
 * 抽象消息事件接口，定义了将消息转化为提示词字符串和数据库存储对象的方法
 * <p>
 * 若想要实现消息事件，请实现此接口。同时，为确保消息能被顺利地从数据库中取出，请实现并注册相应的工厂类。
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
    protected final SNoGenerator.SerialNo serialNo;

    private final String objectType;

    /**
     * @throws NullPointerException 若 `platform`、`messageType` 或 `timestamp` 为空，
     *                              或 `senderInfo` 和 `groupInfo` 同时为空
     */
    public AbstractMessageEvent(
      String platform,
      MessageMeta.EntityInfo senderInfo,
      MessageMeta.StreamInfo streamInfo,
      Long timestamp,
      SNoGenerator.SerialNo serialNo,
      String objectType
    ) {
        this.messageMeta = new MessageMeta(platform, senderInfo, streamInfo);
        this.timestamp = timestamp;
        this.serialNo = serialNo;
        this.objectType = objectType;
    }

    /**
     * 获取消息来源信息
     *
     * @return 消息来源信息对象
     */
    public MessageMeta messageMeta() {
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
    public SNoGenerator.SerialNo sequence() {
        return serialNo;
    }

    /**
     * 将消息转化为提示词字符串
     */
    public abstract String toPromptString(EntityManager em);

    /**
     * 将消息的额外字段转化为数据库存储对象
     * <p>
     * 该方法需要在一个活动的实体管理器上下文中运行，仅生成数据库对象，不进行持久化操作。
     *
     * @param em 实体管理器
     * @return 数据库消息对象
     */
    public Message toDatabaseObject(
      EntityManager em,
      InteractionEntityManager interactionEntityManager,
      InteractionGroupManager interactionGroupManager,
      InteractionStreamManager interactionStreamManager
    ) {
        var message = new Message();

        // 两个查询：
        // 1. 查询 interaction_entity 表，获取发送者实体的 ID
        // 2. 将 interaction_stream 表和 interaction_entity、interaction_group 表关联起来，通过消息类型辨别要
        //     关联Entity的互动流还是Group的互动流，获取互动流的 ID

        // 查询发送者实体
        var senderEntity = interactionEntityManager.get(
          em,
          this.messageMeta.platform(),
          this.messageMeta.senderInfo().platformId()
        );
        message.setSender(senderEntity);

        // 查询互动流
        var streamInfo = this.messageMeta.streamInfo();
        InteractionStream stream;
        switch (streamInfo.streamType()) {
            case PRIVATE -> {
                var ie = requireNonNull(interactionEntityManager.get(
                  em,
                  this.messageMeta.platform(),
                  streamInfo.privateInfo().platformId()
                ));

                stream = requireNonNull(interactionStreamManager.getOrCreateIfAbsent(
                  em,
                  StreamType.PRIVATE,
                  ie,
                  null
                ));
                message.setStream(stream);
            }
            case GROUP -> {
                var ig = requireNonNull(interactionGroupManager.get(
                  em,
                  this.messageMeta.platform(),
                  streamInfo.groupInfo().platformId()
                ));

                stream = requireNonNull(interactionStreamManager.getOrCreateIfAbsent(
                  em,
                  StreamType.GROUP,
                  null,
                  ig
                ));
                message.setStream(stream);
            }
            case GROUP_TEMP -> {
                // 暂不做支持，预期行为同 PRIVATE
            }
            case GROUP_ANONYMOUS -> {
                // 不计划支持
            }
        }

        message.setTimestamp(this.timestamp);
        message.setSequence(this.serialNo.sNo());
        message.setPromptStr(this.toPromptString(em));
        message.setObjectType(this.objectType);

        return message;
    }

    public <T> T unwarp(Class<T> clazz) {
        return UnwrapUtils.unwrap(clazz, this);
    }

    /**
     * 将消息的类型特定内容（非Abstract基类的字段）转化为 JSON 字符串
     *
     * @return 类型特定内容的 JSON 字符串
     */
    protected abstract String toRawContentJson(ObjectMapper objectMapper);

    @Override
    public String toString() {
        return String.format(
          "AbstractMessageEvent[messageSource=%s, timestamp=%d, sequence=%s]",
          messageMeta,
          timestamp,
          serialNo
        );
    }
}
