/* Maibot-JavaEdition - A LLM-based Agent framework written in Java
 * Copyright (C) 2025 Maibot-JE Project Developers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.maibot.core;

import org.maibot.core.commandline.TerminalController;
import org.maibot.core.config.BuildInfo;
import org.maibot.core.config.MainConfig;
import org.maibot.core.ioc.Instance;
import org.maibot.core.log.LogConfig;
import org.maibot.core.modloader.ModManager;
import org.maibot.core.net.InnerServer;
import org.maibot.core.net.client.HttpClientProviderImpl;
import org.maibot.core.thinking.ThinkingFlowManager;
import org.maibot.core.util.TaskExecutorServiceImpl;
import org.maibot.core.util.TimerProxy;
import org.maibot.sdk.TaskExecutorService;
import org.maibot.sdk.config.ConfigService;
import org.maibot.sdk.exceptions.FatalError;
import org.maibot.sdk.exceptions.IgnorableException;
import org.maibot.sdk.ioc.AutoInject;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.net.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("ClassCanBeRecord") // 抑制警告：可以转化为记录类
@Component
public class MaibotMain {
    public static final AtomicReference<LaunchArgs> LAUNCH_ARGS = new AtomicReference<>();

    private static final Logger log = LoggerFactory.getLogger(MaibotMain.class);

    /* 单例资源区 */
    private final TaskExecutorServiceImpl taskExecutorService;
    private final InnerServer             innerServer;
    private final HttpClientProviderImpl  httpClientProvider;
    private final TerminalController      terminalController;
    private final ThinkingFlowManager     thinkingFlowManager;
    private final ModManager              modManager;

    @AutoInject
    public MaibotMain(
      TaskExecutorServiceImpl taskExecutorService,
      InnerServer innerServer,
      HttpClientProviderImpl httpClientProvider,
      TerminalController terminalController,
      ThinkingFlowManager thinkingFlowManager,
      ModManager modManager
    ) {
        this.taskExecutorService = taskExecutorService;
        this.innerServer = innerServer;
        this.httpClientProvider = httpClientProvider;
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

        // 解析命令行参数
        if (args.length != 1) {
            System.err.println("请检查启动参数数量，仅允许传入一个JSON格式的字符串参数");
            System.exit(1);
        }
        LAUNCH_ARGS.set(LaunchArgs.parse(args[0]));

        // 初始化IOC容器
        Instance.scanImplementations("org.maibot", Thread.currentThread().getContextClassLoader());

        var buildInfo = Instance.get(BuildInfo.class);
        System.out.print("""
                         Maibot-JavaEdition Copyright (C) 2025  Maibot-JE Project Developers
                         This program comes with ABSOLUTELY NO WARRANTY; see LICENSE (15.
                         Disclaimer of Warranty.) for details.  This is free software,
                         and you are welcome to redistribute it under certain conditions;
                         also see LICENSE for details.
                         """);
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

        MaibotMain maibotMain = TimerProxy.start(() -> Instance.get(MaibotMain.class), "实例化主类用时：{}ms");

        try {
            maibotMain.run();
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
        // Mod加载需要放在所有组件启动之前
        // 因为组件可能依赖Mod提供的功能
        // Mod加载完成后才能保证组件的正常工作
        TimerProxy.start(
          () -> {
              log.info("正在加载Mod...");
              this.modManager.loadMods();
          }, "加载Mod用时：{}ms"
        );


        var terminalFuture = TimerProxy.start(
          () -> {
              log.info("正在启动思维流...");
              this.thinkingFlowManager.initialize();

              log.info("正在启动网络服务...");
              HttpClient.registerProvider(this.httpClientProvider);
              this.taskExecutorService.submit(this.innerServer::run, true);

              // 启动终端
              log.info("正在启动终端...");
              return this.taskExecutorService.submit(this.terminalController::runCommandline, false);
          }, "启动用时：{}ms"
        );

        // TODO: 启动Mod

        // 阻塞调用，直到终端退出
        terminalFuture.join();
    }
}