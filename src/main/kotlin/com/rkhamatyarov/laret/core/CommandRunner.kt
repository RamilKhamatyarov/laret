package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.fs.DryRunFileSystem
import com.rkhamatyarov.laret.model.fs.LaretFileSystem
import com.rkhamatyarov.laret.model.fs.RealFileSystem
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
        val executionArgs = dryRunArgs(args)
        val fs = fileSystemFor(executionArgs.isDryRun)
        if (groupName == "completion" && command.name in setOf("bash", "zsh", "powershell", "install", "interactive")) {
            val ctx = CommandContext(
                command,
                app,
                groupName = groupName,
                isDryRun = executionArgs.isDryRun,
                fs = fs,
            )
            command.parseArgumentsAndOptions(executionArgs.args, ctx, groupName)
            return try {
                command.action(ctx)
                0
            } catch (e: Exception) {
                System.err.println(e.message)
                1
            }
        }

        val ctx = CommandContext(
            command,
            app,
            groupName = groupName,
            isDryRun = executionArgs.isDryRun,
            fs = fs,
        )
        command.parseArgumentsAndOptions(executionArgs.args, ctx, groupName)

        val positionalCount = executionArgs.args.count { !it.startsWith("-") }
        val missingRequired = command.arguments.filterIndexed { idx, arg ->
            arg.required && !arg.optional && idx >= positionalCount
        }
        if (missingRequired.isNotEmpty()) {
            missingRequired.forEach { HelpFormatter.showArgumentMissingError(it.name) }
            HelpFormatter.showCommandHelp(command)
            return 1
        }

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

    internal fun isDryRunInvocation(app: CliApp, args: Array<String>): Boolean {
        val normalizedArgs = normalizeColonCommand(args)
        val groupInput = normalizedArgs.getOrNull(0) ?: return false
        val commandInput = normalizedArgs.getOrNull(1) ?: return false
        val group = app.groups.find { it.matches(groupInput) } ?: return false
        group.commands.find { it.matches(commandInput) } ?: return false
        return dryRunArgs(normalizedArgs.drop(2).toTypedArray()).isDryRun
    }

    private fun fileSystemFor(isDryRun: Boolean): LaretFileSystem =
        if (isDryRun) DryRunFileSystem() else RealFileSystem()

    private fun normalizeColonCommand(args: Array<String>): Array<String> {
        val commandToken = args.firstOrNull() ?: return args
        if (!commandToken.contains(":")) return args
        val parts = commandToken.split(":", limit = 2)
        return (parts + args.drop(1)).toTypedArray()
    }

    /**
     * Strips the single global `--dry-run` flag from [args].  There is
     * deliberately **no `-n` shorthand**: `-n` is already a command-specific
     * option elsewhere (e.g. `events --max-events`), and a global short flag
     * would shadow it.
     */
    private fun dryRunArgs(args: Array<String>): DryRunArgs {
        var isDryRun = false
        val filtered = mutableListOf<String>()

        args.forEach { arg ->
            if (arg == "--dry-run") isDryRun = true else filtered.add(arg)
        }

        return DryRunArgs(filtered.toTypedArray(), isDryRun)
    }

    // Not a data class: holds an Array and is only accessed by property, never
    // compared or destructured — so generated equals()/hashCode() add no value.
    private class DryRunArgs(val args: Array<String>, val isDryRun: Boolean)
}
