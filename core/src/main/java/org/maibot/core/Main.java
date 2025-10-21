package org.maibot.core;

import org.maibot.core.commandline.TerminalController;
import org.maibot.core.config.ConfigService;
import org.maibot.core.config.VersionInfo;
import org.maibot.core.db.DatabaseService;
import org.maibot.core.cdi.Instance;
import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.event.SystemEventService;
import org.maibot.core.log.LogConfig;
import org.maibot.core.net.InnerServer;
import org.maibot.core.thinking.ThinkingFlowManager;
import org.maibot.core.util.TaskExecutorService;
import org.maibot.core.util.TimerProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("ClassCanBeRecord") // 抑制警告：可以转化为记录类
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /* 单例资源区 */
    private final TaskExecutorService taskExecutorService;
    private final InnerServer innerServer;
    private final TerminalController terminalController;
    private final ThinkingFlowManager thinkingFlowManager;

    @AutoInject
    public Main(
            TaskExecutorService taskExecutorService,
            InnerServer innerServer,
            TerminalController terminalController,
            ThinkingFlowManager thinkingFlowManager
    ) {
        this.taskExecutorService = taskExecutorService;
        this.innerServer = innerServer;
        this.terminalController = terminalController;
        this.thinkingFlowManager = thinkingFlowManager;
    }

    public void run() {
        // 启动网络服务
        TimerProxy.start(() -> {
            log.info("正在启动思维流...");
            this.thinkingFlowManager.initialize();

            log.info("正在启动网络服务...");
            this.taskExecutorService.submit(this.innerServer::run, true);

            // TODO: 初始化Mod管理器并进行模块加载

        }, "启动用时：{}ms");

        // 启动终端
        log.info("正在启动终端...");
        this.terminalController.runCommandline();
    }

    public static void main(String[] args) {
        var versionInfo = Instance.get(VersionInfo.class);
        System.out.printf("<=== MaiBot - JAVA Edition - %s ===>\n", versionInfo.getVersion());
        System.out.printf("> Build Time: %s (UTC) <\n", versionInfo.getBuildTime());

        var configManager = Instance.get(ConfigService.class);
        LogConfig.configure(configManager.get().log);
        System.out.println("日志系统初始化完成");
        // <!-- 从此处开始可以正常使用Logger -->

        TimerProxy.start(() -> {
            log.info("↓正在预载核心组件↓");
            log.info("初始化任务执行器...");
            var taskExecutorService = Instance.get(TaskExecutorService.class);
            // <!-- 从此处开始可以正常使用taskExecutor -->

            log.info("初始化数据库...");
            var databaseService = Instance.get(DatabaseService.class);

            log.info("初始化事件通道...");
            var systemChannel = Instance.get(SystemEventService.class);

            log.info("初始化网络服务...");
            var innerServer = Instance.get(InnerServer.class);

            log.info("初始化思维流管理器...");
            var thinkingFlowManager = Instance.get(ThinkingFlowManager.class);

            log.info("注册关闭钩子...");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.warn("正在关闭 MaiBot...");
                thinkingFlowManager.shutdown();
                innerServer.shutdown();
                systemChannel.close();
                databaseService.close();
                taskExecutorService.shutdown();
                log.info("MaiBot 已成功关闭");
            }));

            log.info("↑核心组件预载完成↑");
        }, "预载用时：{}ms");

        Instance.get(Main.class).run();
        System.exit(0);
    }
}