package io.github.laretframework.dsl

import io.github.laretframework.completion.Completer
import io.github.laretframework.core.CommandContext
import io.github.laretframework.core.Middleware
import io.github.laretframework.model.Argument
import io.github.laretframework.model.Command
import io.github.laretframework.model.Constraint
import io.github.laretframework.model.Option
import io.github.laretframework.model.ValidationBuilder
import io.github.laretframework.model.buildValidators

class CommandBuilder(val name: String, val description: String = "") {
    private val arguments = mutableListOf<Argument>()
    private val options = mutableListOf<Option>()
    private val aliases = mutableListOf<String>()
    private val exclusiveGroups = mutableListOf<Set<String>>()
    private val constraints = mutableListOf<Constraint>()
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

    /** When [name] is set, every name in [required] must have a value. */
    fun requires(name: String, vararg required: String) {
        require(required.isNotEmpty()) { "requires(\"$name\") needs at least one required name" }
        constraints += Constraint.Requires(name, required.toList())
    }

    /** Exactly one of [names] must be set. */
    fun oneOf(vararg names: String) {
        require(names.size >= 2) { "oneOf needs at least two names" }
        constraints += Constraint.OneOf(names.toList())
    }

    /** Either every one of [names] is set, or none is. */
    fun allOrNone(vararg names: String) {
        require(names.size >= 2) { "allOrNone needs at least two names" }
        constraints += Constraint.AllOrNone(names.toList())
    }

    /** When [name] resolves to [equals], every name in [required] must have a value. */
    fun requiredWhen(name: String, equals: String, vararg required: String) {
        require(required.isNotEmpty()) { "requiredWhen(\"$name\") needs at least one required name" }
        constraints += Constraint.RequiredWhen(name, equals, required.toList())
    }

    fun action(block: (CommandContext) -> Unit) {
        actionBlock = block
    }

    fun build(): Command {
        checkConstraintNames()
        return Command(
            name, description, arguments, options, aliases.toList(),
            actionBlock, preExecute, postExecute, onError, hidden,
            exclusiveGroups.toList(), constraints.toList(),
        )
    }

    private fun checkConstraintNames() {
        val referenced = constraints.flatMap { it.names }.toSet()
        val argumentNames = arguments.map { it.name }.toSet()
        val optionNames = options.map { it.long }.toSet()
        val unknown = referenced - argumentNames - optionNames
        require(unknown.isEmpty()) { "Command '$name': constraints refer to unknown names: ${unknown.sorted()}" }
        val ambiguous = referenced.filter { it in argumentNames && it in optionNames }
        require(ambiguous.isEmpty()) {
            "Command '$name': constraint names match both an argument and an option: ${ambiguous.sorted()}"
        }
    }
}
