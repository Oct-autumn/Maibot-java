package org.maibot.core.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("unused")
object TimerProxy {
    fun <T> start(format: String = "Task executed in {} ms", loggerName: String, task: () -> T): T {
        return start(format, LoggerFactory.getLogger(loggerName), task)
    }

    fun <T> start(
        format: String = "Task executed in {} ms",
        log: Logger = LoggerFactory.getLogger("Timer"),
        task: () -> T
    ): T {
        val startTime = System.currentTimeMillis()
        val ret = task.invoke()
        val endTime = System.currentTimeMillis()
        log.debug(format, (endTime - startTime))
        return ret
    }
}
