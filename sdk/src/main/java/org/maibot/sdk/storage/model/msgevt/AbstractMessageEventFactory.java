package org.maibot.sdk.storage.model.msgevt;

import org.maibot.sdk.SNoGenerator;
import org.maibot.sdk.storage.db.dao.Message;
import tools.jackson.databind.ObjectMapper;

public abstract class AbstractMessageEventFactory {
    protected MessageMeta           messageMeta;
    protected Long                  timestamp;
    protected SNoGenerator.SerialNo serialNo;

    protected final String objectType;

    /**
     * @param ameClassName 消息事件类的对象类型名称
     */
    protected AbstractMessageEventFactory(String ameClassName) {
        this.objectType = ameClassName;
    }

    public AbstractMessageEventFactory setMessageMeta(MessageMeta messageMeta) {
        this.messageMeta = messageMeta;
        return this;
    }

    public AbstractMessageEventFactory setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public AbstractMessageEventFactory setSequence(SNoGenerator.SerialNo serialNo) {
        this.serialNo = serialNo;
        return this;
    }

    public MessageMeta getMessageMeta() {
        return messageMeta;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public SNoGenerator.SerialNo getSequence() {
        return serialNo;
    }

    public String getObjectType() {
        return objectType;
    }

    /**
     * 从数据库提取 MessageEvent 信息
     * <p>
     * 建议在EntityManager的上下文中调用此方法，以确保延迟加载的属性能够正确获取。<br>
     * 若在EntityManager上下文之外调用，则需要提前加载相关属性，避免出现LazyInitializationException异常。
     *
     * @param message 数据库消息对象
     * @return 消息事件工厂实例
     * @throws IllegalArgumentException 如果消息的对象类型与当前工厂不匹配
     */
    public AbstractMessageEventFactory fromDatabase(ObjectMapper objectMapper, Message message) {
        if (!message.getObjectType().equals(this.objectType)) {
            throw new IllegalArgumentException("Cannot build message event from " + message.getObjectType() + " to " + this.getClass()
              .getName());
        }

        this.messageMeta = new MessageMetaFactory().fromDatabase(message).build();
        this.timestamp = message.getTimestamp();
        this.serialNo = SNoGenerator.from(message.getSequence());

        return fromRawContentJson(objectMapper, message.getRawContentJson());
    }

    /**
     * 从类型特定内容 JSON 字符串中提取 MessageEvent 信息
     *
     * @param objectMapper JSON 对象映射器
     * @param jsonString   类型特定内容的 JSON 字符串
     * @return 消息事件工厂实例
     */
    protected abstract AbstractMessageEventFactory fromRawContentJson(ObjectMapper objectMapper, String jsonString);

    /**
     * 构建消息事件对象
     *
     * @return 消息事件对象
     */
    public abstract AbstractMessageEvent build();
}
