package org.maibot.core.log

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.jline.reader.LineReader
import org.maibot.core.util.AnsiFormatter.render
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Value
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class CustomTerminalAppender
@AutoInject private constructor(
    @Value($$"${log.enable_mdc_track}") private val enableMdcTrack: Boolean
) : AppenderBase<ILoggingEvent>() {
    var lineReader: LineReader? = null

    override fun append(eventObject: ILoggingEvent) {
        try {
            val msg = encode(eventObject)

            // 若LineReader可用，则使用printAbove方法输出日志；
            // 否则直接使用System.out.print输出
            lineReader?.printAbove(msg) ?: print(msg)
        } catch (e: Exception) {
            // 处理异常
            System.err.println("日志输出失败: " + e.message)
        }
    }

    private fun encode(event: ILoggingEvent): String {
        val levelColor = when (event.level.toString()) {
            "TRACE" -> "FG_BRIGHT_BLACK"
            "DEBUG" -> "FG_BLUE"
            "INFO" -> "FG_GREEN"
            "WARN" -> "FG_YELLOW"
            "ERROR" -> "FG_RED"
            else -> "FG_DEFAULT"
        }

        if (enableMdcTrack) {
            return render(
                LOG_TEMPLATE_WITH_MDC,
                DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.timeStamp).atZone(ZoneId.systemDefault())),
                event.threadName,
                levelColor,
                String.format("%-5s", event.level),
                compressLoggerName(event.loggerName, 30),
                renderMDC(event),
                event.formattedMessage,
                renderThrowable(event)
            )
        } else {
            return render(
                LOG_TEMPLATE,
                DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.timeStamp).atZone(ZoneId.systemDefault())),
                event.threadName,
                levelColor,
                String.format("%-5s", event.level),
                compressLoggerName(event.loggerName, 30),
                event.formattedMessage,
                renderThrowable(event)
            )
        }
    }

    /**
     * 渲染MDC信息
     * 
     * @param event 日志事件
     * @return 渲染后的MDC信息字符串
     */
    private fun renderMDC(event: ILoggingEvent): String {
        if (event.mdcPropertyMap.isEmpty()) {
            return ""
        }

        val mdcEntries = event.mdcPropertyMap.entries.stream().map { entry: MutableMap.MutableEntry<String, String> ->
            render(
                MDC_TEMPLATE, entry.key, entry.value
            )
        }

        val mdcBuilder = StringJoiner(", ", "[", "]")
        mdcEntries.forEach { newElement: String -> mdcBuilder.add(newElement) }

        return mdcBuilder.toString()
    }

    /**
     * 渲染异常信息
     * 
     * @param event 日志事件
     * @return 渲染后的异常信息字符串
     */
    private fun renderThrowable(event: ILoggingEvent): String {
        if (event.throwableProxy == null) {
            return ""
        }

        val throwableBuilder = StringBuilder()

        var throwableProxy = event.throwableProxy
        while (throwableProxy != null) {
            val firstLineBuilder = StringBuilder()
            firstLineBuilder.append("Exception in thread").append(" \"").append(event.threadName).append("\" ")
                .append(throwableProxy.className)

            throwableProxy.message?.let {
                firstLineBuilder.append(": ").append(it)
            }

            val stackTraceBuilder = StringBuilder()
            val stackTraceElements = throwableProxy.stackTraceElementProxyArray
            for (element in stackTraceElements) {
                stackTraceBuilder.append("    ").append(element!!.steAsString).append("\n")
            }

            throwableBuilder.append(
                render(
                    THROWABLE_TEMPLATE, firstLineBuilder.toString(), stackTraceBuilder.toString()
                )
            )

            throwableProxy = throwableProxy.cause
        }

        return throwableBuilder.toString()
    }

    /**
     * 压缩LoggerName
     * 
     * 
     * 当超出指定长度时，依次进行以下压缩尝试： <br></br>
     * 1. 对于点分割的LoggerName，尝试将前缀包名缩写为首字母，直到不超出长度或无法再压缩为止。 <br></br>
     * 1.1. 若全部前缀包名压缩后仍然超出长度，则继续尝试移除最后一个包名的中间部分（中间用...连接），直到不超出长度或无法再压缩为止。 <br></br>
     * 2. 对于非点分割的LoggerName，尝试移除中间部分（中间用...连接），直到不超出长度或无法再压缩为止。 <br></br>
     * 
     * @param loggerName 原始LoggerName
     * @param maxLen     最大长度
     * @return 压缩后的LoggerName
     */
    private fun compressLoggerName(loggerName: String, maxLen: Int): String {
        if (loggerName.length <= maxLen) {
            return loggerName
        }

        val parts = loggerName.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size > 1) {
            // 点分割的LoggerName，尝试缩写包名
            for (i in 0..<parts.size - 1) {
                if (parts[i].length > 1) {
                    parts[i] = parts[i].substring(0, 1) // 缩写为首字母
                }
                val compressed = parts.joinToString(".")
                if (compressed.length <= maxLen) {
                    return compressed
                }
            }

            // 如果全部前缀包名缩写后仍然超出长度，尝试移除最后一个包名的中间部分
            val lastPart = parts[parts.size - 1]
            val availableLen = maxLen - (loggerName.length - lastPart.length) - 3 // 3是"..."的长度
            if (availableLen > 0 && lastPart.length > availableLen) {
                return parts.mapIndexed { idx, part ->
                    if (idx < parts.size - 1) part else // 移除最后一个包名的中间部分
                        part.substring(0, availableLen / 2) + "..." + part.substring(
                            part.length - availableLen / 2
                        )
                }.joinToString(".")
            }
        } else {
            // 非点分割的LoggerName，尝试移除中间部分
            val availableLen = maxLen - 3 // 3是"..."的长度
            if (availableLen > 0 && loggerName.length > availableLen) {
                return loggerName.substring(
                    0, availableLen / 2
                ) + "..." + loggerName.substring(loggerName.length - availableLen / 2)
            }
        }

        // 无法压缩到指定长度，返回原始名称
        return loggerName
    }

    companion object {
        private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss")

        private const val LOG_TEMPLATE = "{1} @{FG_BRIGHT_CYAN [{2}]}@ @{{3} {4}}@ @{FG_CYAN {5}}@:\n\t@{{3} {6}}@\n{7}"
        private const val LOG_TEMPLATE_WITH_MDC =
            "{1} @{FG_BRIGHT_CYAN [{2}]}@ @{{3} {4}}@ @{FG_CYAN {5}}@ {6}:\n\t@{{3} {7}}@\n{8}"
        private const val MDC_TEMPLATE = "@{FG_MAGENTA,BOLD {1}}@=@{FG_MAGENTA {2}}@"
        private const val THROWABLE_TEMPLATE = "@{FG_RED,BOLD {1}}@\n@{FG_RED,FAINT {2}}@\n"
    }
}
