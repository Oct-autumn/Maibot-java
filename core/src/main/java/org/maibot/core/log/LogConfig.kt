package org.maibot.core.log

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.FileAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import org.jline.reader.LineReader
import org.maibot.core.config.CoreConfig
import org.maibot.core.config.CoreConfig.Log.ConsoleLogSettings
import org.maibot.core.config.CoreConfig.Log.FileLogSettings
import org.maibot.core.ioc.Instance
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

object LogConfig {
    /*-!- 不要在此类中使用Logger -!-*/
    private val AVAL_LEVELS = HashSet(
        listOf(
            "TRACE",
            "DEBUG",
            "INFO",
            "WARN",
            "ERROR",
            "OFF"
        )
    )

    @JvmStatic
    fun configure(conf: CoreConfig.Log) {
        (LoggerFactory.getILoggerFactory() as LoggerContext).let { context ->
            context.getLogger("ROOT").apply {
                level = Level.TRACE // 将日志级别设置为 TRACE，以便过滤器可以处理所有级别的日志
                detachAndStopAllAppenders() // 清除现有的 appender

                addAppender(getTerminalAppender(context, conf.console))

                if (!conf.file.level.equals("OFF", ignoreCase = true)) {
                    // 确保日志目录存在
                    val logDir = File("logs")
                    if (!logDir.exists() && logDir.mkdirs()) {
                        println("创建日志目录: " + logDir.absolutePath)
                    }
                    addAppender(getFileAppender(context, conf.file))
                }
            }
        }
    }

    fun getTerminalAppender(
        loggerContext: LoggerContext,
        conf: ConsoleLogSettings
    ): CustomTerminalAppender {
        if (!AVAL_LEVELS.contains(conf.level.uppercase(Locale.getDefault()))) {
            System.out.printf("<!> 无效的终端日志级别：%s，使用默认级别 - DEBUG\n", conf.level)
        }

        return Instance.get(CustomTerminalAppender::class.java).apply {
            name = "terminal"
            context = loggerContext
            addFilter(createCustomFilter(Level.toLevel(conf.level), conf.filterRule))
            start()
        }
    }

    private fun getFileAppender(
        loggerContext: LoggerContext,
        conf: FileLogSettings
    ): FileAppender<ILoggingEvent> {
        if (!AVAL_LEVELS.contains(conf.level.uppercase(Locale.getDefault()))) {
            System.out.printf("<!> 无效的文件日志级别：%s，使用默认级别 - DEBUG\n", conf.level)
        }

        return with(RollingFileAppender<ILoggingEvent>()) {
            name = "file"
            context = loggerContext
            file = "logs/latest.log"

            rollingPolicy = TimeBasedRollingPolicy<ILoggingEvent>().apply {
                context = loggerContext
                fileNamePattern = "logs/maibot-%d{yyyy-MM-dd}.log"
                maxHistory = conf.maxRollingFiles

                setParent(this@with)
                setTotalSizeCap(FileSize(FileSize.MB_COEFFICIENT * conf.maxTotalSizeMb))
                start()
            }

            encoder = PatternLayoutEncoder().apply {
                context = loggerContext
                pattern =
                    "%d{yyyy-MM-dd HH:mm:ss} " +
                            "[%thread] " +
                            "%-5level " +
                            "%logger - " +
                            "%msg" +
                            "%n"
                start()
            }

            addFilter(createCustomFilter(Level.toLevel(conf.level), conf.filterRule))
            start()

            this
        }
    }

    private fun createCustomFilter(defaultLevel: Level, rules: List<String>): CustomFilter {
        return CustomFilter().apply {
            this.defaultLevel = defaultLevel

            rules.map {
                val parts = it.split(":".toRegex()).dropLastWhile { part -> part.isEmpty() }.toTypedArray()
                val level = if (parts.size > 1) parts[1].uppercase(Locale.getDefault()) else "OFF"

                return@map if (!AVAL_LEVELS.contains(level)) {
                    System.out.printf("<!> 无效的日志过滤规则：%s，跳过\n", it)
                    null
                } else {
                    Pair(parts[0], level)
                }
            }.filterNotNull().forEach { (packageName, level) ->
                addRule(packageName, Level.valueOf(level))
            }

            start()
        }
    }

    @JvmStatic
    fun setTerminalLineReader(reader: LineReader?) {
        val logger = LoggerFactory.getLogger("ROOT") as Logger
        val terminalAppender = logger.getAppender("terminal") as CustomTerminalAppender
        terminalAppender.lineReader = reader
    }
}

