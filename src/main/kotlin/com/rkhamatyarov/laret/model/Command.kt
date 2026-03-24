package com.rkhamatyarov.laret.model

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.core.CommandContext

/**
 * A single executable command within a [CommandGroup].
 *
 * @param name        Primary name used to invoke the command (e.g. "create").
 * @param aliases     Alternative names accepted at the CLI (e.g. listOf("c", "new")).
 * @param description Short description shown in help output.
 */
data class Command(
    val name: String,
    val description: String = "",
    val arguments: List<Argument> = emptyList(),
    val options: List<Option> = emptyList(),
    val aliases: List<String> = emptyList(),
    val action: (CommandContext) -> Unit = {},
) {
    fun matches(input: String): Boolean = input == name || input in aliases

    fun execute(
        args: Array<String>,
        app: CliApp? = null,
    ) {
        val ctx = CommandContext(this, app)

        val positional = args.filter { !it.startsWith("-") }
        arguments.forEachIndexed { idx, arg ->
            ctx.arguments[arg.name] = positional.getOrElse(idx) { arg.default }
        }

        options.forEach { opt -> ctx.options[opt.long] = opt.default }

        var i = 0
        while (i < args.size) {
            val token = args[i]
            val opt = options.find { "-${it.short}" == token || "--${it.long}" == token }
            if (opt != null) {
                if (opt.takesValue) {
                    ctx.options[opt.long] = args.getOrElse(i + 1) { opt.default }
                    i += 2
                } else {
                    ctx.options[opt.long] = "true"
                    i++
                }
            } else {
                i++
            }
        }

        action(ctx)
    }
}
