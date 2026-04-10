package org.maibot.core

import org.maibot.core.ioc.Instance.close
import org.maibot.core.util.AnsiFormatter.render
import org.slf4j.Logger

class ShutdownHook(val log: Logger) : Thread() {
    override fun run() {
        log.warn("正在关闭 MaiBot...")
        close()
        log.info("MaiBot 已成功关闭")
        // 彩蛋
        println(
            render(
                "\n>> @{FG#FFB6C1 ··· · · -·-- --- ··- - --- -- --- ·-· ·-· --- ·--}@ <<\n"
            )
        )
    }

    companion object {
        fun register(log: Logger) {
            Runtime.getRuntime().addShutdownHook(ShutdownHook(log).apply { name = "Shutdown-Hook" })
        }
    }
}