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
package org.maibot.core

import org.maibot.core.commandline.TerminalController
import org.maibot.core.config.BuildInfo
import org.maibot.core.config.CoreConfig
import org.maibot.core.ioc.Instance
import org.maibot.core.ioc.Instance.scanImplementations
import org.maibot.core.log.LogConfig
import org.maibot.core.modloader.ModManager
import org.maibot.core.net.InnerServer
import org.maibot.core.net.client.HttpClientProviderImpl
import org.maibot.core.thinking.ThinkingFlowManager
import org.maibot.core.util.AnsiFormatter.render
import org.maibot.core.util.EasterEgg.randEly
import org.maibot.core.util.TaskExecuteServiceImpl
import org.maibot.core.util.TimerProxy.start
import org.maibot.sdk.config.ConfigService
import org.maibot.sdk.exceptions.FatalError
import org.maibot.sdk.exceptions.IgnorableException
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.net.HttpClient.registerProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

// 抑制警告：可以转化为记录类
@Component
class MaibotMain
@AutoInject private constructor(
    private val taskExecutorService: TaskExecuteServiceImpl,
    private val innerServer: InnerServer,
    private val httpClientProvider: HttpClientProviderImpl,
    private val terminalController: TerminalController,
    private val thinkingFlowManager: ThinkingFlowManager,
    private val modManager: ModManager
) {
    fun run() {
        // <!-- 从此处开始可以正常使用来自IoC的线程池 -->

        val terminalFuture = start(
            "启动用时：{}ms"
        ) {
            log.info("正在启动思维流...")
            this.thinkingFlowManager.initialize()

            log.info("正在启动网络服务...")
            registerProvider(this.httpClientProvider)
            this.taskExecutorService.submit(true) { this.innerServer.start() }

            // 启动终端
            log.info("正在启动终端...")
            this.taskExecutorService.submit(false) { this.terminalController.runCommandline() }
        }

        // 启用Mod
        this.modManager.enableMods()

        // 阻塞调用，直到终端退出
        terminalFuture.join()
    }

    companion object {
        val LAUNCH_ARGS = AtomicReference<LaunchArgs>()

        private val log: Logger = LoggerFactory.getLogger(MaibotMain::class.java)

        /**
         * 主方法
         * 
         * @param args 命令行参数
         */
        @JvmStatic
        fun main(args: Array<String>) {
            // 不允许在此方法中再次抛出异常
            // 所有未捕获异常均视为致命错误，记录日志后终止运行

            Thread.currentThread().setName("Main")

            // 解析命令行参数
            if (args.size != 1) {
                System.err.println("请检查启动参数数量，仅允许传入一个JSON格式的字符串参数")
                exitProcess(1)
            }
            LAUNCH_ARGS.set(LaunchArgs.parse(args[0]))

            // 初始化IOC容器
            scanImplementations(Thread.currentThread().getContextClassLoader(), "org.maibot")

            print(
                """
                         EgoBot Copyright (C) 2025  EgoBot Project Developers
                         This program comes with ABSOLUTELY NO WARRANTY; see LICENSE (15.
                         Disclaimer of Warranty.) for details.  This is free software,
                         and you are welcome to redistribute it under certain conditions;
                         also see LICENSE for details.
                         
                         """.trimIndent()
            )
            print(
                """
                         _|_|_|_|                      _|_|_|                _|     
                         _|          _|_|_|    _|_|    _|    _|    _|_|    _|_|_|_| 
                         _|_|_|    _|    _|  _|    _|  _|_|_|    _|    _|    _|     
                         _|        _|    _|  _|    _|  _|    _|  _|    _|    _|     
                         _|_|_|_|    _|_|_|    _|_|    _|_|_|      _|_|        _|_| 
                                         _|                                         
                                     _|_|                                           
                         
                         """.trimIndent()
            )
            val buildInfo = Instance.get(BuildInfo::class.java)
            System.out.printf("<=== EgoBot - %s ===>\n", buildInfo.coreVersion.version)
            System.out.printf("> Build Time: %s (UTC) <\n", buildInfo.buildTime)
            System.out.printf("> SDK Version: %s <\n", buildInfo.sdkVersion.version)

            val configService = Instance.get(ConfigService::class.java)
            LogConfig.configure(
                configService.getConfig(
                    "log", CoreConfig.Log::class.java
                )
            )
            log.info("日志系统初始化完成")

            // <!-- 从此处开始可以正常使用Logger -->

            // 彩蛋
            println(render("\n@{FG#FFB6C1 {}}@\n", randEly()))

            log.info("注册关闭钩子...")
            ShutdownHook.register(log)

            // Mod加载需要放在所有组件启动之前
            // 因为组件可能依赖Mod提供的功能
            // Mod加载完成后才能保证组件的正常工作
            start("加载Mod用时：{}ms") {
                val modManager = Instance.get(ModManager::class.java)
                modManager.loadMods(LAUNCH_ARGS.get()!!.modList)
            }

            val maibotMain = start("实例化主类用时：{}ms") {
                Instance.get(MaibotMain::class.java)
            }

            try {
                maibotMain.run()
            } catch (e: IgnorableException) {
                log.warn("未捕获的可忽略异常：{}", e.message)
            } catch (e: FatalError) {
                log.error("发生致命错误，终止运行", e)
                exitProcess(1)
            } catch (e: Exception) {
                log.error("运行时发生未捕获的异常，终止运行", e)
                exitProcess(1)
            }
            exitProcess(0)
        }
    }
}