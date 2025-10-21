package org.maibot.core.commandline;

import org.maibot.core.cdi.Instance;
import picocli.CommandLine;

public class Commands {
    @CommandLine.Command(name = "", description = "MaiBot - JAVA Edition Shell", subcommands = {ExitCommand.class})
    static class ShellCommand implements Runnable {
        @Override
        public void run() {
        }
    }

    @CommandLine.Command(name = "exit", description = "Exit and stop the bot")
    static class ExitCommand implements Runnable {
        @Override
        public void run() {
            System.out.println("Exiting...");
            Instance.get(TerminalController.class).stopCommandline();
        }
    }
}
