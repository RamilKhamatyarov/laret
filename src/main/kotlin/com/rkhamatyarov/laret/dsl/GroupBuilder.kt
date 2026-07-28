package com.rkhamatyarov.laret.dsl

import com.rkhamatyarov.laret.core.Middleware
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup

/**
 * DSL builder for a [CommandGroup].
 *
 * Usage:
 * ```kotlin
 * group(name = "file", description = "File operations") {
 *     aliases("f", "files")
 *     command(name = "create") { ... }
 * }
 * ```
 */
class GroupBuilder(val name: String, val description: String = "") {
    private val commands = mutableListOf<Command>()
    private val aliases = mutableListOf<String>()

    /** Middleware registered at GROUP scope, and per-command registrations collected from nested `command { }` blocks. */
    internal val middlewares = mutableListOf<Middleware>()
    internal val commandMiddlewares = mutableMapOf<String, List<Middleware>>()

    /**
     * Register middleware at GROUP scope: it runs for every command in this
     * group. The target is the enclosing group, so the same middleware class
     * can be reused in another group without modification.
     */
    fun use(vararg middleware: Middleware) {
        middlewares.addAll(middleware)
    }

    /**
     * Register one or more alternative names for this group.
     *
     * Example: `aliases("f")` lets users type `laret f create …`
     * instead of `laret file create …`.
     */
    fun aliases(vararg names: String) {
        aliases.addAll(names)
    }

    /** Define a command within this group. */
    fun command(name: String, description: String = "", block: CommandBuilder.() -> Unit) {
        val cmdBuilder = CommandBuilder(name, description)
        cmdBuilder.block()
        commands.add(cmdBuilder.build())
        if (cmdBuilder.middlewares.isNotEmpty()) {
            commandMiddlewares[name] = cmdBuilder.middlewares.toList()
        }
    }

    fun build(): CommandGroup = CommandGroup(name, description, commands.toList(), aliases.toList())
}
