package com.rkhamatyarov.laret.dsl

import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.model.Argument
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.Option

/**
 * DSL builder for a single [Command].
 *
 * Usage:
 * ```kotlin
 * command(name = "remove", description = "Remove a file") {
 *     aliases("rm", "del")
 *     argument("path", "File path")
 *     option("f", "force", "Skip confirmation")
 *     action { ctx -> ... }
 * }
 * ```
 */
class CommandBuilder(
    val name: String,
    val description: String = "",
) {
    private val arguments = mutableListOf<Argument>()
    private val options = mutableListOf<Option>()
    private val aliases = mutableListOf<String>()
    private var actionBlock: (CommandContext) -> Unit = {}

    /**
     * Register one or more alternative names for this command.
     *
     * All aliases are matched case-sensitively at runtime.  Aliases are shown
     * in help output alongside the primary name.
     *
     * Example: `aliases("rm", "del")`
     */
    fun aliases(vararg names: String) {
        aliases.addAll(names)
    }

    /** Define a positional argument. */
    fun argument(
        name: String,
        description: String = "",
        required: Boolean = true,
        optional: Boolean = false,
        default: String = "",
    ) {
        arguments.add(
            Argument(
                name,
                description,
                required,
                optional,
                default,
            ),
        )
    }

    /** Define a command-line option / flag. */
    fun option(
        short: String,
        long: String,
        description: String = "",
        default: String = "",
        takesValue: Boolean = true,
    ) {
        options.add(
            Option(
                short,
                long,
                description,
                default,
                takesValue,
            ),
        )
    }

    /** Define the action executed when this command is invoked. */
    fun action(block: (CommandContext) -> Unit) {
        actionBlock = block
    }

    fun build(): Command = Command(name, description, arguments, options, aliases.toList(), actionBlock)
}
