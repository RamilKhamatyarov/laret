package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.ui.redBold
import org.slf4j.LoggerFactory

/**
 * Handles command execution logic
 */
object CommandRunner {
    private val log = LoggerFactory.getLogger(javaClass::class.java)

    fun execute(
        app: CliApp,
        args: Array<String>,
    ) {
        val groupName = args.getOrNull(0) ?: return

        if (args.size == 2 && (args[1] == "-h" || args[1] == "--help")) {
            val group = app.groups.find { it.name == groupName }
            if (group != null) {
                HelpFormatter.showGroupHelp(group)
                return
            }
        }

        val commandName = args.getOrNull(1) ?: return
        val cmdArgs = args.drop(2)

        val group =
            app.groups.find { it.name == groupName }
                ?: run {
                    log.warn(redBold("Group not found: $groupName"))
                    HelpFormatter.showApplicationHelp(app)
                    return
                }

        val command =
            group.commands.find { it.name == commandName }
                ?: run {
                    log.warn(redBold("Command not found: $commandName"))
                    HelpFormatter.showCommandNotFound(commandName, group)
                    return
                }

        command.execute(cmdArgs.toTypedArray(), app)
    }
}
