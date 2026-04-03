package org.maibot.core.commandline

import org.maibot.core.ioc.Instance
import picocli.CommandLine

class Commands {
    @CommandLine.Command(name = "", description = ["MaiBot - JAVA Edition Shell"], subcommands = [ExitCommand::class])
    internal class ShellCommand : Runnable {
        override fun run() {
        }
    }

    @CommandLine.Command(name = "exit", description = ["Exit and stop the bot"])
    internal class ExitCommand : Runnable {
        override fun run() {
            println("Exiting...")
            Instance.get(TerminalController::class.java).stopCommandline()
        }
    }

    @CommandLine.Command(name = "mod", description = ["Manage mods"], subcommands = [ModListCommand::class])
    internal class ModCommand : Runnable {
        override fun run() {
        }
    }

    @CommandLine.Command(name = "list", description = ["List all loaded mods"])
    internal class ModListCommand : Runnable {
        override fun run() {
            // TODO: Implement mod listing
        }
    }
}
