package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.ui.redBold
import kotlinx.coroutines.runBlocking

object CommandRunner {
    internal var globalMiddlewares: List<Middleware> = emptyList()
    internal var groupMiddlewares: Map<String, List<Middleware>> = emptyMap()
    internal var commandMiddlewares: Map<String, List<Middleware>> = emptyMap()

    fun execute(app: CliApp, args: Array<String>): Int {
        val groupInput = args.getOrNull(0) ?: return 0

        if (args.size == 2 && (args[1] == "-h" || args[1] == "--help")) {
            val group = app.groups.find { it.matches(groupInput) }
            if (group != null) {
                HelpFormatter.showGroupHelp(group)
                return 0
            }
        }

        val commandInput = args.getOrNull(1) ?: return 0
        val cmdArgs = args.drop(2).toTypedArray()

        val group = app.groups.find { it.matches(groupInput) }
            ?: run {
                println(redBold("Group not found: $groupInput"))
                HelpFormatter.showApplicationHelp(app)
                return 1
            }

        val command = group.commands.find { it.matches(commandInput) }
            ?: run {
                println(redBold("Command not found: $commandInput"))
                HelpFormatter.showCommandNotFound(commandInput, group)
                return 1
            }

        return runBlocking {
            executeCommand(command, cmdArgs, app, group.name)
        }
    }

    internal suspend fun executeCommand(command: Command, args: Array<String>, app: CliApp, groupName: String): Int {
        if (groupName == "completion" && command.name in setOf("bash", "zsh", "powershell", "install", "interactive")) {
            val ctx = CommandContext(command, app, groupName = groupName)
            command.parseArgumentsAndOptions(args, ctx, groupName)
            return try {
                command.action(ctx)
                0
            } catch (e: Exception) {
                System.err.println(e.message)
                1
            }
        }

        val ctx = CommandContext(command, app, groupName = groupName)
        command.parseArgumentsAndOptions(args, ctx, groupName)

        try {
            command.preExecute.invoke(ctx)
        } catch (e: Exception) {
            System.err.println(e.message ?: "Pre‑execution failed")
            return 1
        }

        val groupMws = groupMiddlewares[groupName] ?: emptyList()
        val commandMws = commandMiddlewares[command.name] ?: emptyList()
        val allMiddlewares = (globalMiddlewares + groupMws + commandMws).sortedBy { it.priority }

        val chain = MiddlewareChain(allMiddlewares, command.action)

        return try {
            chain.execute(ctx)
            command.postExecute.invoke(ctx)
            0
        } catch (e: Exception) {
            command.onError.invoke(ctx, e)
            1
        }
    }
}
