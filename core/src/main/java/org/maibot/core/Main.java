package org.maibot.core;

import org.maibot.core.config.ConfigService;
import org.maibot.core.config.VersionInfo;
import org.maibot.core.db.DatabaseService;
import org.maibot.core.cdi.InstanceManager;
import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.event.SystemEventService;
import org.maibot.core.log.LogConfig;
import org.maibot.core.net.InnerServer;
import org.maibot.core.util.TaskExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private final TaskExecutorService taskExecutorService;
    private final InnerServer innerServer;
    private final TerminalController terminalController;

    @AutoInject
    public Main(TaskExecutorService taskExecutorService, InnerServer innerServer, TerminalController terminalController) {
        this.taskExecutorService = taskExecutorService;
        this.innerServer = innerServer;
        this.terminalController = terminalController;
    }

    public int run() {
        // 启动网络服务
        var timer = System.currentTimeMillis();

        log.info("正在启动网络服务...");
        this.taskExecutorService.submit(this.innerServer::run, true);

        // TODO: 初始化决策中枢
        // TODO: 初始化Mod管理器并进行模块加载

        // 启动终端
        log.info("正在启动终端...");
        this.terminalController.runCommandline();

        return 0;
    }

    public static void main(String[] args) {
        var versionInfo = InstanceManager.getInstance(VersionInfo.class);
        System.out.printf("<=== MaiBot - JAVA Edition - %s ===>\n", versionInfo.getVersion());
        System.out.printf("> Build Time: %s (UTC) <\n", versionInfo.getBuildTime());

        var timer = System.currentTimeMillis();

        var configManager = InstanceManager.getInstance(ConfigService.class);
        LogConfig.configure(configManager.get().log);
        System.out.println("日志系统初始化完成");
        // <!-- 从此处开始可以正常使用Logger -->

        log.info("↓正在预载核心组件↓");
        log.info("初始化任务执行器...");
        var taskExecutorService = InstanceManager.getInstance(TaskExecutorService.class);
        // <!-- 从此处开始可以正常使用taskExecutor -->

        log.info("初始化数据库...");
        var databaseService = InstanceManager.getInstance(DatabaseService.class);

        log.info("初始化事件通道...");
        var systemChannel = InstanceManager.getInstance(SystemEventService.class);

        log.info("初始化网络服务...");
        var innerServer = InstanceManager.getInstance(InnerServer.class);

        log.info("↑核心组件预载完成↑");

        log.info("注册关闭钩子...");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.warn("正在关闭 MaiBot...");
            innerServer.shutdown();
            systemChannel.close();
            databaseService.close();
            taskExecutorService.shutdown();
            log.info("MaiBot 已成功关闭");
        }));

        log.info("预载用时: {} ms", System.currentTimeMillis() - timer);

        var main = InstanceManager.getInstance(Main.class);
        System.exit(main.run());
    }
}