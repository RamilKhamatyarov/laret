package com.rkhamatyarov.laret.dsl

import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.model.Argument
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.Option

class CommandBuilder(val name: String, val description: String = "") {
    private val arguments = mutableListOf<Argument>()
    private val options = mutableListOf<Option>()
    private val aliases = mutableListOf<String>()
    private var actionBlock: (CommandContext) -> Unit = {}

    var preExecute: suspend (CommandContext) -> Unit = {}
    var postExecute: suspend (CommandContext) -> Unit = {}
    var onError: suspend (CommandContext, Exception) -> Unit = { _, _ -> }

    fun aliases(vararg names: String) {
        aliases.addAll(names)
    }

    fun argument(
        name: String,
        description: String = "",
        required: Boolean = true,
        optional: Boolean = false,
        default: String = "",
    ) {
        arguments.add(Argument(name, description, required, optional, default))
    }

    fun option(
        short: String,
        long: String,
        description: String = "",
        default: String = "",
        takesValue: Boolean = true,
        persistent: Boolean = false,
    ) {
        options.add(Option(short, long, description, default, takesValue, persistent))
    }

    fun action(block: (CommandContext) -> Unit) {
        actionBlock = block
    }

    fun build(): Command = Command(
        name, description, arguments, options, aliases.toList(),
        actionBlock, preExecute, postExecute, onError,
    )
}
