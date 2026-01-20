package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.output.OutputStrategy
import com.rkhamatyarov.laret.output.PlainOutput

/**
 * Context passed to command actions
 */
class CommandContext(
    val command: Command,
    val app: CliApp? = null,
    val outputStrategy: OutputStrategy = PlainOutput,
) {
    val arguments = mutableMapOf<String, String>()

    val options = mutableMapOf<String, String>()

    fun argument(name: String): String = arguments[name] ?: ""

    fun option(name: String): String = options[name] ?: ""

    fun optionBool(name: String): Boolean = options[name]?.toBoolean() ?: false

    fun optionInt(name: String): Int = options[name]?.toIntOrNull() ?: 0

    fun render(data: Any): String = outputStrategy.render(data)
}
