package org.maibot.core;

import lombok.Setter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.TerminalBuilder;
import org.maibot.core.cdi.annotation.Component;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliJLineCompleter;

@Component
public class TerminalController {
    private final LineReader reader;
    private final CommandLine cmd;

    private boolean running;

    @Setter
    private String prompt;

    private TerminalController() throws Exception {
        var terminal = TerminalBuilder.builder().system(true).build();

        this.cmd = new CommandLine(new ShellCommand());
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new PicocliJLineCompleter(cmd.getCommandSpec()))
                .build();
        this.running = false;

        this.prompt = "> ";
    }

    public void runTerminal() {
        this.running = true;
        while (this.running) {
            String line = reader.readLine(this.prompt);
            cmd.execute(line.split("\\s+"));
        }
    }

    public void stopTerminal() {
        this.running = false;
    }
}

@CommandLine.Command(name = "", description = "MaiBot - JAVA Edition Shell",
        subcommands = {})
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
        System.exit(0);
    }
}
