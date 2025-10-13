package org.maibot.core.event;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.maibot.core.cdi.annotation.Component;
import org.slf4j.LoggerFactory;

@Component
public class SystemEventService {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(SystemEventService.class);
    private final Channel channel;

    public SystemEventService() {
        this.channel = new EmbeddedChannel();
    }

    public void addHandler(String name, ChannelHandler handler) {
        this.channel.pipeline().addLast(name, handler);
    }

    public void removeHandler(String name) {
        this.channel.pipeline().remove(name);
    }

    public void close() {
        try {
            this.channel.close();
        } catch (Exception e) {
            log.error("关闭系统事件服务时发生错误", e);
        }

    }
}
