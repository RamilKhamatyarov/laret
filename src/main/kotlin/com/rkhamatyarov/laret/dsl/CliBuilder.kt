package com.rkhamatyarov.laret.dsl

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.core.CommandRunner
import com.rkhamatyarov.laret.core.Middleware
import com.rkhamatyarov.laret.core.MiddlewareListCommand
import com.rkhamatyarov.laret.core.MiddlewareRegistration
import com.rkhamatyarov.laret.core.MiddlewareRegistry
import com.rkhamatyarov.laret.core.MiddlewareScope
import com.rkhamatyarov.laret.model.CommandGroup

/** Builds CLI application */
class CliBuilder(val name: String, val version: String, val description: String) {
    private val groups = mutableListOf<CommandGroup>()
    private val middlewares = mutableListOf<Middleware>()

    private val groupRegistrations = mutableListOf<MiddlewareRegistration>()
    private val commandRegistrations = mutableListOf<MiddlewareRegistration>()

    var onAppInit: suspend (CliApp) -> Unit = {}
    var onAppShutdown: suspend (CliApp) -> Unit = {}

    fun group(name: String, description: String = "", block: GroupBuilder.() -> Unit) {
        val groupBuilder = GroupBuilder(name, description)
        groupBuilder.block()
        groups.add(groupBuilder.build())

        groupBuilder.middlewares.forEach {
            groupRegistrations += MiddlewareRegistration(it, MiddlewareScope.GROUP, name)
        }
        groupBuilder.commandMiddlewares.forEach { (commandName, commandMiddlewares) ->
            commandMiddlewares.forEach {
                commandRegistrations += MiddlewareRegistration(
                    it,
                    MiddlewareScope.COMMAND,
                    "$name $commandName",
                )
            }
        }
    }

    /**
     * Register middleware at GLOBAL scope: it runs for every command.
     *
     * Register inside a `group { }` or `command { }` block instead to narrow
     * the scope — the registration site is what determines it.
     */
    fun use(vararg middleware: Middleware) {
        middlewares.addAll(middleware)
    }

    fun build(): CliApp {
        val globalRegistrations = middlewares.map {
            MiddlewareRegistration(it, MiddlewareScope.GLOBAL, "*")
        }
        val registry = MiddlewareRegistry.of(globalRegistrations, groupRegistrations, commandRegistrations)

        CommandRunner.globalMiddlewares = middlewares.toList()

        if (groups.none { it.name == MIDDLEWARE_GROUP }) {
            groups.add(buildMiddlewareGroup())
        }

        val app = CliApp(name, version, description, groups, registry)
        app.onInitHook = onAppInit
        app.onShutdownHook = onAppShutdown
        return app
    }

    /**
     * Chain inspection, auto-registered for every Laret application so the
     * effective order is debuggable without reading the wiring source.
     */
    private fun buildMiddlewareGroup(): CommandGroup {
        val builder = GroupBuilder(MIDDLEWARE_GROUP, "Middleware chain inspection")
        builder.command(name = "list", description = "List registered middleware or preview a command's chain") {
            option("c", "command", "Preview the chain for a command, e.g. \"file delete\"", "", true)
            action { ctx ->
                val registry = ctx.app?.middlewares ?: MiddlewareRegistry.EMPTY
                val renderer = MiddlewareListCommand(registry)
                val target = ctx.option("command")
                if (target.isBlank()) {
                    print(renderer.renderRegistry())
                } else {
                    val parsed = renderer.parseTarget(target)
                    if (parsed == null) {
                        System.err.println("Invalid --command value: '$target' (expected \"<group> <command>\")")
                    } else {
                        print(renderer.renderChain(parsed.first, parsed.second))
                    }
                }
            }
        }
        return builder.build()
    }

    companion object {
        const val MIDDLEWARE_GROUP = "middleware"
    }
}
