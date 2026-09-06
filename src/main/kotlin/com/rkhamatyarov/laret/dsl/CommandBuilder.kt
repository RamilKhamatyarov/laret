package com.rkhamatyarov.laret.dsl

import com.rkhamatyarov.laret.completion.Completer
import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.core.Middleware
import com.rkhamatyarov.laret.model.Argument
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.Option
import com.rkhamatyarov.laret.model.ValidationBuilder
import com.rkhamatyarov.laret.model.buildValidators

class CommandBuilder(val name: String, val description: String = "") {
    private val arguments = mutableListOf<Argument>()
    private val options = mutableListOf<Option>()
    private val aliases = mutableListOf<String>()
    private val exclusiveGroups = mutableListOf<Set<String>>()
    private var hidden = false
    private var actionBlock: (CommandContext) -> Unit = {}

    internal val middlewares = mutableListOf<Middleware>()

    /**
     * Register middleware at COMMAND scope: it runs only for this command.
     *
     * Ordering across scopes is by priority alone, so a low enough priority
     * here still wraps a global middleware.
     */
    fun use(vararg middleware: Middleware) {
        middlewares.addAll(middleware)
    }

    var preExecute: suspend (CommandContext) -> Unit = {}
    var postExecute: suspend (CommandContext) -> Unit = {}
    var onError: suspend (CommandContext, Exception) -> Unit = { _, _ -> }

    fun aliases(vararg names: String) {
        aliases.addAll(names)
    }

    /** Marks this command as hidden: excluded from help/completion and from doc navigation. */
    fun hidden() {
        hidden = true
    }

    /**
     * Declare a positional argument.
     *
     * @param validate optional rules for the resolved value, e.g.
     *   `argument("port") { range(1, 65535) }`.
     */
    fun argument(
        name: String,
        description: String = "",
        required: Boolean = true,
        optional: Boolean = false,
        default: String = "",
        completer: Completer? = null,
        validate: (ValidationBuilder.() -> Unit)? = null,
    ) {
        arguments.add(
            Argument(name, description, required, optional, default, completer, buildValidators(validate)),
        )
    }

    /**
     * Declare a named option.
     *
     * @param validate optional rules for the resolved value, e.g.
     *   `option("f", "format") { oneOf("json", "yaml") }`.
     */
    fun option(
        short: String,
        long: String,
        description: String = "",
        default: String = "",
        takesValue: Boolean = true,
        persistent: Boolean = false,
        configKey: String? = null,
        completer: Completer? = null,
        validate: (ValidationBuilder.() -> Unit)? = null,
    ) {
        options.add(
            Option(
                short,
                long,
                description,
                default,
                takesValue,
                persistent,
                configKey,
                completer,
                buildValidators(validate),
            ),
        )
    }

    /**
     * Declare that at most one of [longNames] may be supplied. The rule fires
     * only when two or more are explicitly passed on the command line.
     */
    fun mutuallyExclusive(vararg longNames: String) {
        if (longNames.size >= 2) exclusiveGroups.add(longNames.toSet())
    }

    fun action(block: (CommandContext) -> Unit) {
        actionBlock = block
    }

    fun build(): Command = Command(
        name, description, arguments, options, aliases.toList(),
        actionBlock, preExecute, postExecute, onError, hidden,
        exclusiveGroups.toList(),
    )
}
