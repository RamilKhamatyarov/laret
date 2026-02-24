package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.ui.redBold
import org.fusesource.jansi.Ansi

/**
 * Centralized formatter for displaying help messages across the CLI application.
 */
object HelpFormatter {
    /**
     * Display help for the entire CLI application
     */
    fun showApplicationHelp(app: CliApp) {
        println(
            """
            ${Ansi.ansi().bold().fg(Ansi.Color.CYAN)}========================================${Ansi.ansi().reset()}
            ${Ansi.ansi().bold().fg(Ansi.Color.CYAN)}${app.name} v${app.version}${Ansi.ansi().reset()}
            ${app.description}

            ${Ansi.ansi().bold().fg(Ansi.Color.CYAN)}========================================${Ansi.ansi().reset()}

            ${Ansi.ansi().bold()}USAGE:${Ansi.ansi().reset()}
            ${app.name} [COMMAND] [SUBCOMMAND] [OPTIONS]

            ${Ansi.ansi().bold()}COMMANDS:${Ansi.ansi().reset()}
            ${formatCommandGroups(app.groups)}

            ${Ansi.ansi().bold()}GLOBAL OPTIONS:${Ansi.ansi().reset()}
            -h, --help ${" ".repeat(15)} Show this help message
            -v, --version ${" ".repeat(12)} Show version

            ${Ansi.ansi().bold()}EXAMPLES:${Ansi.ansi().reset()}
            ${app.name} file create /tmp/test.txt --content "hello"
            ${app.name} dir list . --long --all
            ${app.name} completion bash > completion.sh

            For more information on a command, use:
            ${app.name} [COMMAND] --help
            """.trimIndent(),
        )
    }

    /**
     * Display help for a specific command group
     */
    fun showGroupHelp(group: CommandGroup) {
        System.err.println("Group: ${group.name} - ${group.description}")
        System.err.println("\nCommands:")
        group.commands.forEach { cmd ->
            System.err.println("  ${cmd.name.padEnd(20)} ${cmd.description}")
        }
    }

    /**
     * Display help for a specific command
     */
    fun showCommandHelp(command: Command) {
        System.err.println("Command: ${command.name}")
        if (command.description.isNotEmpty()) {
            System.err.println("Description: ${command.description}\n")
        }

        if (command.arguments.isNotEmpty()) {
            System.err.println("Arguments:")
            command.arguments.forEach { arg ->
                val req = if (arg.required) "required" else "optional"
                System.err.println("  ${arg.name.padEnd(20)} $req - ${arg.description}")
            }
        }

        if (command.options.isNotEmpty()) {
            System.err.println("\nOptions:")
            command.options.forEach { opt ->
                val flags = "-${opt.short}, --${opt.long}".padEnd(25)
                System.err.println("  $flags ${opt.description}")
            }
        }
    }

    /**
     * Format command groups for display in help text
     */
    private fun formatCommandGroups(groups: List<CommandGroup>): String =
        groups.joinToString("\n") { group ->
            val groupHeader = "${Ansi.ansi().fg(Ansi.Color.GREEN)}${group.name}${Ansi.ansi().reset()} ${group.description}"
            val commandsList =
                group.commands.joinToString("\n") { command ->
                    "  ${Ansi.ansi().fg(Ansi.Color.BLUE)}${command.name}${Ansi.ansi().reset()} ${command.description}"
                }
            "$groupHeader\n$commandsList"
        }

    /**
     * Display a command not found error with suggestions
     */
    fun showCommandNotFound(
        commandName: String,
        group: CommandGroup? = null,
    ) {
        println(redBold("Error: Command not found: $commandName"))
        if (group != null) {
            showGroupHelp(group)
        }
    }

    /**
     * Display a required argument missing error
     */
    fun showArgumentMissingError(argumentName: String) {
        println(redBold("Error: Required argument '$argumentName' not provided"))
    }
}
