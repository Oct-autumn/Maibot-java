package org.maibot.sdk.model.msgevt;

import org.maibot.sdk.db.dao.Message;

public interface AbstractMessageEventFactory {
    /**
     * 从数据库消息对象构建消息事件对象
     *
     * @param message 数据库消息对象
     * @return 消息事件对象
     */
    AbstractMessageEvent fromDatabaseObject(Message message);
}
