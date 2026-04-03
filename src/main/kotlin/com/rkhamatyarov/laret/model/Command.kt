package com.rkhamatyarov.laret.model

import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.core.FlagPersistence

/**
 * A single executable command within a [CommandGroup].
 *
 * @param name        Primary name used to invoke the command (e.g. `"create"`).
 * @param aliases     Alternative names accepted at the CLI (e.g. `listOf("c", "new")`).
 * @param description Short description shown in help output.
 */
data class Command(
    val name: String,
    val description: String = "",
    val arguments: List<Argument> = emptyList(),
    val options: List<Option> = emptyList(),
    val aliases: List<String> = emptyList(),
    val action: (CommandContext) -> Unit = {},
    val preExecute: suspend (CommandContext) -> Unit = {},
    val postExecute: suspend (CommandContext) -> Unit = {},
    val onError: suspend (CommandContext, Exception) -> Unit = { _, _ -> },
) {
    /** True when [input] equals the primary name or any alias. */
    fun matches(input: String): Boolean = input == name || input in aliases

    internal fun parseArgumentsAndOptions(args: Array<String>, ctx: CommandContext, groupName: String) {
        val positional = args.filter { !it.startsWith("-") }
        arguments.forEachIndexed { idx, arg ->
            ctx.arguments[arg.name] = positional.getOrElse(idx) { arg.default }
        }

        val providedOptions = mutableMapOf<String, String>()
        var i = 0
        while (i < args.size) {
            val token = args[i]
            val opt = options.find { "-${it.short}" == token || "--${it.long}" == token }
            if (opt != null) {
                if (opt.takesValue) {
                    providedOptions[opt.long] = args.getOrElse(i + 1) { opt.default }
                    i += 2
                } else {
                    providedOptions[opt.long] = "true"
                    i++
                }
            } else {
                i++
            }
        }

        val config = ctx.app?.getAppConfig()
        options.forEach { option ->
            val cliValue = providedOptions[option.long]
            if (cliValue != null) {
                ctx.options[option.long] = cliValue
            } else if (option.persistent) {
                val configValue = FlagPersistence.resolveFlag(option, groupName, this.name, config)
                ctx.options[option.long] = configValue ?: option.default
            } else {
                ctx.options[option.long] = option.default
            }
        }
    }
}
