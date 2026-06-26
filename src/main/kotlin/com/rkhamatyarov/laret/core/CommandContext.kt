package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.config.registry.ConfigRegistry
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.fs.LaretFileSystem
import com.rkhamatyarov.laret.model.fs.RealFileSystem
import com.rkhamatyarov.laret.output.OutputStrategy
import com.rkhamatyarov.laret.output.PlainOutput
import com.rkhamatyarov.laret.ui.InteractivePrompt
import com.rkhamatyarov.laret.ui.ProgressBar
import com.rkhamatyarov.laret.ui.Spinner

class CommandContext(
    val command: Command,
    val app: CliApp? = null,
    val outputStrategy: OutputStrategy = PlainOutput,
    val groupName: String,
    var config: ConfigRegistry = ConfigRegistry.empty(),
    val isDryRun: Boolean = false,
    val fs: LaretFileSystem = RealFileSystem(),
) {
    val arguments = mutableMapOf<String, String>()

    val options = mutableMapOf<String, String>()

    fun argument(name: String): String = arguments[name] ?: ""

    fun option(name: String): String = options[name] ?: ""

    fun optionBool(name: String): Boolean = options[name]?.toBoolean() ?: false

    fun optionInt(name: String): Int = options[name]?.toIntOrNull() ?: 0

    fun optionLong(name: String): Long = options[name]?.toLongOrNull() ?: 0L

    @Suppress("unused") // public DSL accessor, completes the option* family
    fun optionDouble(name: String): Double = options[name]?.toDoubleOrNull() ?: 0.0

    fun argumentInt(name: String): Int = argument(name).toIntOrNull() ?: 0

    @Suppress("unused") // public DSL accessor, completes the argument* family
    fun argumentLong(name: String): Long = argument(name).toLongOrNull() ?: 0L

    fun render(data: Any): String = outputStrategy.render(data)

    fun progressBar(total: Int, label: String = "", width: Int = 40): ProgressBar =
        ProgressBar(total = total, width = width, label = label, enabled = isInteractive())

    fun spinner(label: String = ""): Spinner = Spinner(label = label, enabled = isInteractive())

    fun prompt(): InteractivePrompt = InteractivePrompt(enabled = isInteractive())

    fun registerUndo(description: String, undoArgs: Array<String>, redoArgs: Array<String> = emptyArray()) {
        UndoManager.push(
            UndoManager.newEntry(description, undoArgs.toList(), redoArgs.toList()),
            isDryRun = isDryRun,
        )
    }
}
