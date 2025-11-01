package org.maibot.core.commandline;

import org.maibot.core.ioc.Instance;
import org.maibot.core.modloader.ModManager;
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

    @CommandLine.Command(name = "mod", description = "Manage mods", subcommands = {ModListCommand.class})
    static class ModCommand implements Runnable {
        @Override
        public void run() {
        }
    }

    @CommandLine.Command(name = "list", description = "List all loaded mods")
    static class ModListCommand implements Runnable {
        @Override
        public void run() {
            // TODO: Implement mod listing
        }
    }
}
