package org.maibot.core.commandline;

import lombok.Setter;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.maibot.core.log.LogConfig;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.ioc.DestroyableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliJLineCompleter;

import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class TerminalController implements DestroyableComponent {
    private static final Logger log = LoggerFactory.getLogger(TerminalController.class);

    private final Terminal terminal;

    private final ReentrantLock lock         = new ReentrantLock();
    private final Condition     endCondition = lock.newCondition();

    private boolean        running;
    private CommandLine    cmd;
    private LineReaderImpl reader;

    @Setter
    private String prompt;

    private TerminalController()
    throws Exception {
        this.terminal = TerminalBuilder.builder().system(true).build();
        this.running = false;

        this.prompt = "Cmd> ";
    }

    public void runCommandline() {
        this.cmd = new CommandLine(new Commands.ShellCommand());
        this.reader = (LineReaderImpl) LineReaderBuilder.builder()
                                                        .terminal(this.terminal)
                                                        .completer(new PicocliJLineCompleter(cmd.getCommandSpec()))
                                                        .build();
        this.running = true;
        LogConfig.setTerminalLineReader(this.reader);
        while (this.running) {
            try {
                String line = this.reader.readLine(this.prompt);
                cmd.execute(line.split("\\s+"));
            } catch (EndOfFileException e) {
                log.warn("无法读取终端输入，正在退出命令行");
                break;
            }
        }

        if (!this.running) {
            return;
        }

        try {
            // 无输入命令行的Fallback
            // 阻塞当前线程，直到收到停止信号
            lock.lock();
            endCondition.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    public void closeTerminal() {
        try {
            this.terminal.close();
        } catch (IOException e) {
            log.error("关闭终端时发生错误", e);
        }
    }

    @Override
    public void preDestroy() {
        stopCommandline();
    }

    /**
     * 停止终端（当前正在运行时）
     */
    public void stopCommandline() {
        this.running = false;
        try {
            lock.lock();
            endCondition.signalAll();
        } finally {
            lock.unlock();
        }
        LogConfig.setTerminalLineReader(null);
    }
}
