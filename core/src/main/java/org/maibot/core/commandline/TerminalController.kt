package org.maibot.core.commandline

import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.LineReaderImpl
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.maibot.core.log.LogConfig.setTerminalLineReader
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.DestroyableComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.shell.jline3.PicocliJLineCompleter
import java.io.IOException
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

@Component
class TerminalController private constructor() : DestroyableComponent {
    private val terminal: Terminal = TerminalBuilder.builder().system(true).build()

    private val lock = ReentrantLock()
    private val endCondition: Condition = lock.newCondition()

    private var running = false
    private var cmd: CommandLine? = null
    private var reader: LineReaderImpl? = null

    private val prompt: String = "Cmd> "

    fun runCommandline() {
        this.cmd = CommandLine(Commands.ShellCommand())
        this.reader = LineReaderBuilder.builder()
            .terminal(this.terminal)
            .completer(PicocliJLineCompleter(cmd!!.commandSpec))
            .build() as LineReaderImpl
        this.running = true
        setTerminalLineReader(this.reader)
        while (this.running) {
            try {
                val line = this.reader!!.readLine(this.prompt)
                cmd!!.execute(*line.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            } catch (_: EndOfFileException) {
                log.warn("无法读取终端输入，正在退出命令行")
                break
            }
        }

        if (!this.running) {
            return
        }

        try {
            // 无输入命令行的Fallback
            // 阻塞当前线程，直到收到停止信号
            lock.lock()
            endCondition.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            lock.unlock()
        }
    }

    fun closeTerminal() {
        try {
            this.terminal.close()
        } catch (e: IOException) {
            log.error("关闭终端时发生错误", e)
        }
    }

    override fun preDestroy() {
        stopCommandline()
    }

    /**
     * 停止终端（当前正在运行时）
     */
    fun stopCommandline() {
        this.running = false
        try {
            lock.lock()
            endCondition.signalAll()
        } finally {
            lock.unlock()
        }
        setTerminalLineReader(null)
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TerminalController::class.java)
    }
}
