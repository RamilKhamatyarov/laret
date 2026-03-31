package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.ui.redBold

/**
 * Stateless helper that resolves and executes a command from raw CLI arguments.
 *
 * Both group lookup and command lookup use [matches] so that aliases registered
 * via the DSL are transparently honoured here too.
 */
object CommandRunner {
    fun execute(app: CliApp, args: Array<String>) {
        val groupInput = args.getOrNull(0) ?: return

        if (args.size == 2 && (args[1] == "-h" || args[1] == "--help")) {
            val group = app.groups.find { it.matches(groupInput) }
            if (group != null) {
                HelpFormatter.showGroupHelp(group)
                return
            }
        }

        val commandInput = args.getOrNull(1) ?: return
        val cmdArgs = args.drop(2)

        val group =
            app.groups.find { it.matches(groupInput) }
                ?: run {
                    println(redBold("Group not found: $groupInput"))
                    HelpFormatter.showApplicationHelp(app)
                    return
                }

        val command =
            group.commands.find { it.matches(commandInput) }
                ?: run {
                    println(redBold("Command not found: $commandInput"))
                    HelpFormatter.showCommandNotFound(commandInput, group)
                    return
                }

        command.execute(cmdArgs.toTypedArray(), app, group.name)
    }
}
