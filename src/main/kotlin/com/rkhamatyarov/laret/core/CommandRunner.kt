package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.ui.redBold
import kotlinx.coroutines.runBlocking

object CommandRunner {
    internal var globalMiddlewares: List<Middleware> = emptyList()
    internal var groupMiddlewares: Map<String, List<Middleware>> = emptyMap()
    internal var commandMiddlewares: Map<String, List<Middleware>> = emptyMap()

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
        val cmdArgs = args.drop(2).toTypedArray()

        val group = app.groups.find { it.matches(groupInput) }
            ?: run {
                println(redBold("Group not found: $groupInput"))
                HelpFormatter.showApplicationHelp(app)
                return
            }

        val command = group.commands.find { it.matches(commandInput) }
            ?: run {
                println(redBold("Command not found: $commandInput"))
                HelpFormatter.showCommandNotFound(commandInput, group)
                return
            }

        runBlocking {
            executeCommand(command, cmdArgs, app, group.name)
        }
    }

    internal suspend fun executeCommand(command: Command, args: Array<String>, app: CliApp, groupName: String) {
        val ctx = CommandContext(command, app, groupName = groupName)
        command.parseArgumentsAndOptions(args, ctx, groupName)

        val applicable = mutableListOf<Middleware>().apply {
            addAll(globalMiddlewares)
            addAll(groupMiddlewares[groupName] ?: emptyList())
            addAll(commandMiddlewares["$groupName:${command.name}"] ?: emptyList())
        }.sortedBy { it.priority }

        val chain = MiddlewareChain(applicable) { innerCtx ->
            try {
                command.preExecute.invoke(innerCtx)
                command.action(innerCtx)
                command.postExecute.invoke(innerCtx)
            } catch (e: Exception) {
                command.onError.invoke(innerCtx, e)
            }
        }
        chain.execute(ctx)
    }
}
