package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.output.OutputStrategy
import com.rkhamatyarov.laret.output.PlainOutput
import com.rkhamatyarov.laret.ui.InteractivePrompt
import com.rkhamatyarov.laret.ui.ProgressBar
import com.rkhamatyarov.laret.ui.Spinner

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

    fun progressBar(
        total: Int,
        label: String = "",
        width: Int = 40,
    ): ProgressBar = ProgressBar(total = total, width = width, label = label)

    fun spinner(label: String = ""): Spinner = Spinner(label = label)

    fun prompt(): InteractivePrompt = InteractivePrompt()
}
