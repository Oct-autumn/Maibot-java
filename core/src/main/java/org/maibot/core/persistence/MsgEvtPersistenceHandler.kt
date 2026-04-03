package org.maibot.core.persistence;

import io.netty.channel.ChannelHandlerContext;
import org.maibot.core.manager.InteractionEntityManagerImpl;
import org.maibot.core.manager.InteractionGroupManagerImpl;
import org.maibot.core.manager.InteractionStreamManagerImpl;
import org.maibot.sdk.eventchannel.SimpleEventHandler;
import org.maibot.sdk.ioc.AutoInject;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.storage.model.msgevt.AbstractMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Component
public class MsgEvtPersistenceHandler extends SimpleEventHandler<AbstractMessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(MsgEvtPersistenceHandler.class.getName());

    private final DatabaseServiceImpl          databaseService;
    private final InteractionEntityManagerImpl interactionEntityManager;
    private final InteractionGroupManagerImpl  interactionGroupManager;
    private final InteractionStreamManagerImpl interactionStreamManager;

    @AutoInject
    private MsgEvtPersistenceHandler(
      DatabaseServiceImpl databaseService,
      InteractionEntityManagerImpl interactionEntityManager,
      InteractionGroupManagerImpl interactionGroupManager,
      InteractionStreamManagerImpl interactionStreamManager
    ) {
        this.databaseService = databaseService;
        this.interactionEntityManager = interactionEntityManager;
        this.interactionGroupManager = interactionGroupManager;
        this.interactionStreamManager = interactionStreamManager;
    }

    @Override
    protected boolean handleEvent(ChannelHandlerContext ctx, AbstractMessageEvent msg) {
        this.databaseService.execAsync((em) -> {
            MDC.put("sNo", msg.sequence().toHexString());
            var message = msg.toDatabaseObject(
              em,
              this.interactionEntityManager,
              this.interactionGroupManager,
              this.interactionStreamManager
            );
            em.persist(message);
        }).exceptionally(throwable -> {
            log.error("持久化消息事件时发生错误：{}", msg, throwable);
            return null;
        });
        return true;
    }
}
