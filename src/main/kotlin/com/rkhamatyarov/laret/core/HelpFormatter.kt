package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.ui.blue
import com.rkhamatyarov.laret.ui.bold
import com.rkhamatyarov.laret.ui.cyanBold
import com.rkhamatyarov.laret.ui.green
import com.rkhamatyarov.laret.ui.redBold

/** Centralized formatter for displaying help messages across the CLI application. */
object HelpFormatter {
    /** Display help for the entire CLI application */
    fun showApplicationHelp(app: CliApp) {
        val divider = cyanBold("========================================")
        println(
            """
            $divider
            ${cyanBold("${app.name} v${app.version}")}
            ${app.description}

            $divider

            ${bold("USAGE:")}
            ${app.name} [COMMAND] [SUBCOMMAND] [OPTIONS]

            ${bold("COMMANDS:")}
            ${formatCommandGroups(app.groups)}
            ${formatPlugins(app)}

            ${bold("GLOBAL OPTIONS:")}
            -h, --help ${" ".repeat(15)} Show this help message
            -v, --version ${" ".repeat(12)} Show version

            ${bold("EXAMPLES:")}
            ${app.name} file create /tmp/test.txt --content "hello"
            ${app.name} dir list . --long --all
            ${app.name} completion bash > completion.sh

            For more information on a command, use:
            ${app.name} [COMMAND] --help
            """.trimIndent(),
        )
    }

    private fun formatPlugins(app: CliApp): String = app.getSidecarPlugins()
        .filter { it.status == com.rkhamatyarov.laret.plugin.model.PluginStatus.INSTALLED }
        .joinToString("\n") { plugin ->
            "${plugin.name} Execute installed sidecar plugin"
        }

    /** Display help for a specific command group */
    fun showGroupHelp(group: CommandGroup) {
        System.err.println("Group: ${group.name} - ${group.description}")
        System.err.println("\nCommands:")
        group.commands.forEach { cmd ->
            System.err.println("  ${cmd.name.padEnd(20)} ${cmd.description}")
        }
    }

    /** Display help for a specific command */
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

    /** Format command groups for display in help text */
    private fun formatCommandGroups(groups: List<CommandGroup>): String = groups.joinToString("\n") { group ->
        val groupHeader = "${green(group.name)} ${group.description}"
        val commandsList =
            group.commands.joinToString("\n") { command ->
                "  ${blue(command.name)} ${command.description}"
            }
        "$groupHeader\n$commandsList"
    }

    /** Display a command not found error with suggestions */
    fun showCommandNotFound(commandName: String, group: CommandGroup? = null) {
        println(redBold("Error: Command not found: $commandName"))
        if (group != null) {
            showGroupHelp(group)
        }
    }

    /** Display a required argument missing error */
    fun showArgumentMissingError(argumentName: String) {
        println(redBold("Error: Required argument '$argumentName' not provided"))
    }
}
