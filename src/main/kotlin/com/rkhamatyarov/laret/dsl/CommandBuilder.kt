package com.rkhamatyarov.laret.dsl

import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.model.Argument
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.Option

/**
 * Builds a single command
 */
class CommandBuilder(
    val name: String,
    val description: String = ""
) {
    private val arguments = mutableListOf<Argument>()
    private val options = mutableListOf<Option>()
    private var actionBlock: (CommandContext) -> Unit = {}

    /**
     * Define a positional argument
     */
    fun argument(
        name: String,
        description: String = "",
        required: Boolean = true,
        optional: Boolean = false,
        default: String = ""
    ) {
        arguments.add(
            Argument(
                name,
                description,
                required,
                optional,
                default
            )
        )
    }

    /**
     * Define a command-line option/flag
     */
    fun option(
        short: String,
        long: String,
        description: String = "",
        default: String = "",
        takesValue: Boolean = true
    ) {
        options.add(
            Option(
                short,
                long,
                description,
                default,
                takesValue
            )
        )
    }

    /**
     * Define the action to execute
     */
    fun action(block: (CommandContext) -> Unit) {
        actionBlock = block
    }

    fun build(): Command =
        Command(name, description, arguments, options, actionBlock)
}