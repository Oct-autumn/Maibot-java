package org.maibot.core;

import org.maibot.core.cdi.Instance;
import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.commandline.TerminalController;
import org.maibot.core.config.BuildInfo;
import org.maibot.core.config.ConfigService;
import org.maibot.core.db.DatabaseService;
import org.maibot.core.event.SystemEventService;
import org.maibot.core.log.LogConfig;
import org.maibot.core.modloader.ModManager;
import org.maibot.core.net.InnerServer;
import org.maibot.core.thinking.ThinkingFlowManager;
import org.maibot.core.util.TaskExecutorService;
import org.maibot.core.util.TimerProxy;
import org.maibot.sdk.exceptions.FatalError;
import org.maibot.sdk.exceptions.IgnorableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("ClassCanBeRecord") // 抑制警告：可以转化为记录类
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

              // TODO: 初始化Mod管理器并进行模块加载

          }, "启动用时：{}ms"
        );

        // 启动终端
        log.info("正在启动终端...");
        this.terminalController.runCommandline();   // 阻塞调用，直到终端退出

        this.terminalController.closeTerminal();
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

        var configManager = Instance.get(ConfigService.class);
        LogConfig.configure(configManager.get().log);
        System.out.println("日志系统初始化完成");
        // <!-- 从此处开始可以正常使用Logger -->

        TimerProxy.start(
          () -> {
              log.info("↓正在预载核心组件↓");
              log.info("初始化任务执行器...");
              var taskExecutorService = Instance.get(TaskExecutorService.class);
              // <!-- 从此处开始可以正常使用taskExecutor -->

              log.info("初始化数据库...");
              var databaseServiceFuture =
                taskExecutorService.submit(() -> Instance.get(DatabaseService.class), false);

              log.info("初始化事件通道...");
              var systemChannelFuture =
                taskExecutorService.submit(() -> Instance.get(SystemEventService.class), false);

              log.info("初始化网络服务...");
              var innerServerFuture =
                taskExecutorService.submit(() -> Instance.get(InnerServer.class), false);

              log.info("初始化思维流管理器...");
              var thinkingFlowManagerFuture =
                taskExecutorService.submit(() -> Instance.get(ThinkingFlowManager.class), false);

              try {
                  var databaseService = databaseServiceFuture.get();
                  var systemChannel = systemChannelFuture.get();
                  var innerServer = innerServerFuture.get();
                  var thinkingFlowManager = thinkingFlowManagerFuture.get();

                  Thread shutdownThread = new Thread(() -> {
                      log.warn("正在关闭 MaiBot...");
                      thinkingFlowManager.shutdown();
                      innerServer.shutdown();
                      systemChannel.close();
                      databaseService.close();
                      taskExecutorService.shutdown();
                      log.info("MaiBot 已成功关闭");
                  });

                  log.info("注册关闭钩子...");
                  shutdownThread.setName("Shutdown-Hook");
                  Runtime.getRuntime().addShutdownHook(shutdownThread);
              } catch (CancellationException | InterruptedException e) {
                  log.error("并行化预载核心组件时被取消或中断，终止运行", e);
                  System.exit(1);
              } catch (ExecutionException e) {
                  log.error("并行化预载核心组件时发生异常，终止运行", e);
                  System.exit(1);
              }

              log.info("↑核心组件预载完成↑");
          }, "预载用时：{}ms"
        );

        Main main = TimerProxy.start(() -> Instance.get(Main.class), "实例化主类用时：{}ms");

        try {
            main.run();
        } catch (IgnorableException e) {
            // 已知可忽略的异常，记录日志后继续运行
            log.trace("未捕获的可忽略异常：{}", e.getMessage());
        } catch (FatalError e) {
            log.error("发生致命错误，终止运行", e);
            System.exit(1);
        } catch (Exception e) {
            log.error("运行时发生未捕获的异常，终止运行", e);
            System.exit(1);
        }
        System.exit(0);
    }
}