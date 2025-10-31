package org.maibot.core;

import org.maibot.core.commandline.TerminalController;
import org.maibot.core.config.BuildInfo;
import org.maibot.core.config.MainConfig;
import org.maibot.core.ioc.Instance;
import org.maibot.core.log.LogConfig;
import org.maibot.core.modloader.ModManager;
import org.maibot.core.net.InnerServer;
import org.maibot.core.thinking.ThinkingFlowManager;
import org.maibot.core.util.TimerProxy;
import org.maibot.sdk.TaskExecutorService;
import org.maibot.sdk.config.ConfigService;
import org.maibot.sdk.exceptions.FatalError;
import org.maibot.sdk.exceptions.IgnorableException;
import org.maibot.sdk.ioc.AutoInject;
import org.maibot.sdk.ioc.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("ClassCanBeRecord") // 抑制警告：可以转化为记录类
@Component
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /* 单例资源区 */
    private final TaskExecutorService taskExecutorService;
    private final InnerServer         innerServer;
    private final TerminalController  terminalController;
    private final ThinkingFlowManager thinkingFlowManager;
    private final ModManager          modManager;

    @AutoInject
    public Main(
      TaskExecutorService taskExecutorService,
      InnerServer innerServer,
      TerminalController terminalController,
      ThinkingFlowManager thinkingFlowManager,
      ModManager modManager
    ) {
        this.taskExecutorService = taskExecutorService;
        this.innerServer = innerServer;
        this.terminalController = terminalController;
        this.thinkingFlowManager = thinkingFlowManager;
        this.modManager = modManager;
    }

    /**
     * 主方法
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 不允许在此方法中再次抛出异常
        // 所有未捕获异常均视为致命错误，记录日志后终止运行

        Thread.currentThread().setName("Main");

        Instance.scanImplementations("org.maibot", Thread.currentThread().getContextClassLoader());

        var buildInfo = Instance.get(BuildInfo.class);
        System.out.print("""
                           __  __           _   _               _                 _   _____\s
                          |  \\/  |   __ _  (_) | |__     ___   | |_              | | | ____|
                          | |\\/| |  / _` | | | | '_ \\   / _ \\  | __|  _____   _  | | |  _| \s
                          | |  | | | (_| | | | | |_) | | (_) | | |_  |_____| | |_| | | |___\s
                          |_|  |_|  \\__,_| |_| |_.__/   \\___/   \\__|          \\___/  |_____|
                         """);
        System.out.printf("<=== MaiBot - JAVA Edition - %s ===>\n", buildInfo.coreVersion().getVersion());
        System.out.printf("> Build Time: %s (UTC) <\n", buildInfo.getBuildTime());
        System.out.printf("> SDK Version: %s <\n", buildInfo.sdkVersion().getVersion());

        var configService = Instance.get(ConfigService.class);
        LogConfig.configure(configService.getConfig("log", MainConfig.Log.class));
        System.out.println("日志系统初始化完成");
        // <!-- 从此处开始可以正常使用Logger -->

        Instance.get(TaskExecutorService.class);
        log.info("线程池初始化完成");
        // <!-- 从此处开始可以正常使用线程池 -->

        log.info("注册关闭钩子...");
        Thread shutdownThread = new Thread(() -> {
            log.warn("正在关闭 MaiBot...");
            Instance.close();
            log.info("MaiBot 已成功关闭");
        });
        shutdownThread.setName("Shutdown-Hook");
        Runtime.getRuntime().addShutdownHook(shutdownThread);

        Main main = TimerProxy.start(() -> Instance.get(Main.class), "实例化主类用时：{}ms");

        try {
            main.run();
        } catch (IgnorableException e) {
            log.warn("未捕获的可忽略异常：{}", e.getMessage());
        } catch (FatalError e) {
            log.error("发生致命错误，终止运行", e);
            System.exit(1);
        } catch (Exception e) {
            log.error("运行时发生未捕获的异常，终止运行", e);
            System.exit(1);
        }
        System.exit(0);
    }

    public void run() {
        // 启动网络服务
        TimerProxy.start(
          () -> {
              log.info("正在启动思维流...");
              this.thinkingFlowManager.initialize();

              log.info("正在启动网络服务...");
              this.taskExecutorService.submit(this.innerServer::run, true);

              log.info("正在加载Mod...");
              this.modManager.loadMods();

          }, "启动用时：{}ms"
        );

        // 启动终端
        log.info("正在启动终端...");
        this.terminalController.runCommandline();   // 阻塞调用，直到终端退出
    }
}