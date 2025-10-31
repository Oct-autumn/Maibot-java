package org.maibot.mods;

import org.maibot.sdk.ioc.AutoInject;
import org.maibot.sdk.mod.Mod;
import org.maibot.sdk.mod.ModMainClass;
import org.maibot.sdk.net.HttpRouter;
import org.maibot.sdk.net.WsProcessors;
import org.maibot.sdk.net.WsRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@ModMainClass(author = "Maibot Team", description = "Example Adapter Mod")
public class ExampleAdapterMod extends Mod {
    private static final Logger log = LoggerFactory.getLogger(ExampleAdapterMod.class);

    @AutoInject
    private ExampleAdapterMod(HttpRouter httpRouter, WsRouter wsRouter) {
        // 将 PingPongHandler 注册到 HttpDispatcher 中
        // 当接收到特定的 HTTP 请求时，PingPongHandler 会处理这些请求并返回响应
        httpRouter.registerProcessor(new PingPongHandler());

        // 将 EchoHandler 注册到 WsDispatcher 中
        // 当有 WebSocket 连接请求到达 /ws/echo 路径时，
        // Dispatcher会自动将EchoHandler实例装配到该连接的处理链中-
        wsRouter.registerProcessor(new WsProcessors("/ws/echo", List.of(EchoHandler.class)));
    }

    @Override
    public void onLoad() {
        // Register HttpHandler here

        log.info("ExampleAdapterMod has been loaded.");
    }
}