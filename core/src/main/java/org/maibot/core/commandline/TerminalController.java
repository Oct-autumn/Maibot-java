package org.maibot.core.commandline;

import lombok.Setter;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.maibot.core.cdi.Instance;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.core.log.LogConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliJLineCompleter;

import java.io.IOException;

@Component
public class TerminalController {
    private static final Logger log = LoggerFactory.getLogger(TerminalController.class);

    private final Terminal terminal;

    private boolean running;
    private CommandLine cmd;
    private LineReaderImpl reader;

    @Setter
    private String prompt;

    private TerminalController() throws Exception {
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
            String line = this.reader.readLine(this.prompt);
            cmd.execute(line.split("\\s+"));
        }
    }

    /**
     * 停止终端（当前
     */
    public void stopCommandline() {
        if (!this.running) {
            return;
        }
        this.running = false;
        LogConfig.setTerminalLineReader(null);
    }

    public void closeTerminal() {
        try {
            this.terminal.close();
        } catch (IOException e) {
            log.error("关闭终端时发生错误", e);
        }
    }
}
