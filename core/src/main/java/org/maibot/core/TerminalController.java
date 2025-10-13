package org.maibot.core;

import lombok.NonNull;
import lombok.Setter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.maibot.core.cdi.InstanceManager;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.core.log.LogConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliJLineCompleter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

@Component
public class TerminalController {
    private static final Logger log = LoggerFactory.getLogger(TerminalController.class);

    public static class TerminalOutputStream extends OutputStream {
        private final Writer writer;

        public TerminalOutputStream(Terminal terminal) {
            this.writer = terminal.writer();
        }

        @Override
        public void write(int b) throws IOException {
            this.writer.write(b);
        }

        @Override
        public void write(byte @NonNull [] b, int off, int len) throws IOException {
            this.writer.write(new String(b, off, len, StandardCharsets.UTF_8));
        }

        @Override
        public void flush() throws IOException {
            this.writer.flush();
        }

        @Override
        public void close() throws IOException {
            this.writer.close();
        }
    }

    private final Terminal terminal;
    private final TerminalOutputStream terminalOutputStream;
    private final LineReader reader;
    private final CommandLine cmd;

    private boolean running;

    @Setter
    private String prompt;

    private TerminalController() throws Exception {
        this.terminal = TerminalBuilder.builder().system(true).build();
        this.terminalOutputStream = new TerminalOutputStream(this.terminal);
        this.cmd = new CommandLine(new ShellCommand());
        this.reader = LineReaderBuilder.builder()
                .terminal(this.terminal)
                .completer(new PicocliJLineCompleter(cmd.getCommandSpec()))
                .build();
        this.running = false;

        this.prompt = "Cmd> ";
    }

    public void runTerminal() {
        this.running = true;
        LogConfig.redirectConsoleOutputStream(this.terminalOutputStream);
        while (this.running) {
            String line = reader.readLine(this.prompt);
            cmd.execute(line.split("\\s+"));
        }
    }

    /**
     * 停止终端（当前
     */
    public void stopTerminal() {
        synchronized (this) {
            if (!this.running) {
                return;
            }
            try {
                LogConfig.redirectConsoleOutputStream(System.out);
                this.running = false;
                this.terminalOutputStream.close();
                this.terminal.close();
            } catch (IOException e) {
                log.error("关闭终端时发生错误", e);
            }
        }
    }
}

@CommandLine.Command(name = "", description = "MaiBot - JAVA Edition Shell",
        subcommands = {ExitCommand.class})
class ShellCommand implements Runnable {
    @Override
    public void run() {
    }
}

@CommandLine.Command(name = "exit", description = "Exit and stop the bot")
class ExitCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Exiting...");
        InstanceManager.getInstance(TerminalController.class).stopTerminal();
    }
}
