package org.maibot.core.event;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.maibot.core.cdi.annotation.Component;

@Component
public class SystemChannel {
    private final Channel channel;

    public SystemChannel() {
        this.channel = new EmbeddedChannel();
    }

    public void addHandler(String name, ChannelHandler handler) {
        this.channel.pipeline().addLast(name, handler);
    }

    public void removeHandler(String name) {
        this.channel.pipeline().remove(name);
    }

    public void close() {
        this.channel.close();
    }
}
