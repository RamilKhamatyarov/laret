package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.ui.redBold
import org.fusesource.jansi.Ansi
import org.slf4j.LoggerFactory

/**
 * Centralized formatter for displaying help messages across the CLI application.
 */
object HelpFormatter {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Display help for the entire CLI application
     */
    fun showApplicationHelp(app: CliApp) {
        println(
            """
            ${Ansi.ansi().bold().fg(Ansi.Color.CYAN)}Ansi.ansi().bold().fg(
                Ansi.Color.CYAN,
            )}========================================${Ansi.ansi().reset()}
            ${Ansi.ansi().bold().fg(Ansi.Color.CYAN)}Ansi.ansi().bold().fg(
                Ansi.Color.CYAN,
            )}${app.name} v${app.version}${Ansi.ansi().reset()}
            ${app.description}app.description}

            ${Ansi.ansi().bold().fg(Ansi.Color.CYAN)}Ansi.ansi().bold().fg(
                Ansi.Color.CYAN,
            )}========================================${Ansi.ansi().reset()}

            ${Ansi.ansi().bold()}Ansi.ansi().bold()}USAGE:${Ansi.ansi().reset()}
            ${app.name}app.name} [COMMAND] [SUBCOMMAND] [OPTIONS]

            ${Ansi.ansi().bold()}Ansi.ansi().bold()}COMMANDS:${Ansi.ansi().reset()}
            ${formatCommandGroups(app.groups)}formatCommandGroups(app.groups)}

            ${Ansi.ansi().bold()}Ansi.ansi().bold()}GLOBAL OPTIONS:${Ansi.ansi().reset()}
            -h, --help ${" ".repeat(15)} Show this help message
            -v, --version ${" ".repeat(12)} Show version

            ${Ansi.ansi().bold()}Ansi.ansi().bold()}EXAMPLES:${Ansi.ansi().reset()}
            ${app.name}app.name} file create /tmp/test.txt --content "hello"
            ${app.name}app.name} dir list . --long --all
            ${app.name}app.name} completion bash > completion.sh

            For more information on a command, use:
            ${app.name}app.name} [COMMAND] --help
            """.trimIndent(),
        )
    }

    /**
     * Display help for a specific command group
     */
    fun showGroupHelp(group: CommandGroup) {
        log.info("Group: ${group.name} - ${group.description}")
        log.info("\nCommands:")
        group.commands.forEach { cmd ->
            log.info(" ${cmd.name.padEnd(20)} ${cmd.description}")
        }
    }

    /**
     * Display help for a specific command
     */
    fun showCommandHelp(command: Command) {
        log.info("Command: ${command.name}")
        if (command.description.isNotEmpty()) {
            log.info("Description: ${command.description}\n")
        }

        if (command.arguments.isNotEmpty()) {
            log.info("Arguments:")
            command.arguments.forEach { arg ->
                val req = if (arg.required) "required" else "optional"
                log.info(" ${arg.name.padEnd(20)} $req - ${arg.description}")
            }
        }

        if (command.options.isNotEmpty()) {
            log.info("\nOptions:")
            command.options.forEach { opt ->
                val flags = "-${opt.short}, --${opt.long}".padEnd(25)
                log.info(" $flags ${opt.description}")
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
            log.info("\nAvailable commands in group '${group.name}':")
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
