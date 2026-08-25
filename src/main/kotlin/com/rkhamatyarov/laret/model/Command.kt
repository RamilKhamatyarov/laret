package com.rkhamatyarov.laret.model

import com.rkhamatyarov.laret.config.registry.ConfigRegistry
import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.core.FlagPersistence
import com.rkhamatyarov.laret.core.Suggestions

/**
 * A single executable command within a [CommandGroup].
 *
 * @param name        Primary name used to invoke the command (e.g. `"create"`).
 * @param aliases     Alternative names accepted at the CLI (e.g. `listOf("c", "new")`).
 * @param description Short description shown in help output.
 * @param hidden      When `true`, the command is omitted from help/completion and
 *                    from generated documentation navigation; doc generation still
 *                    emits its page (with an `[INTERNAL]` badge) under `--include-hidden`.
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
    val hidden: Boolean = false,
) {
    /** True when [input] equals the primary name or any alias. */
    fun matches(input: String): Boolean = input == name || input in aliases

    internal fun parseArgumentsAndOptions(args: Array<String>, ctx: CommandContext, groupName: String) {
        val positional = args.filter { !it.startsWith("-") }
        arguments.forEachIndexed { idx, arg ->
            ctx.arguments[arg.name] = positional.getOrElse(idx) { arg.default }
        }

        val providedOptions = mutableMapOf<String, String>()
        val unknownFlags = mutableListOf<String>()
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
                if (looksLikeFlag(token)) unknownFlags.add(token)
                i++
            }
        }
        warnUnknownFlags(unknownFlags)

        return finishParsing(ctx, groupName, providedOptions)
    }

    /**
     * True for a token that is an unknown option worth warning about — a `-x`
     * or `--xyz` starting with a letter. Bare dashes and negative numbers
     * (e.g. `-5`) are excluded to avoid false positives on positional values.
     */
    private fun looksLikeFlag(token: String): Boolean = FLAG_TOKEN.containsMatchIn(token) && token !in GLOBAL_FLAGS

    /** Emits a "did you mean?" warning per unrecognized flag (never fails the command). */
    private fun warnUnknownFlags(unknownFlags: List<String>) {
        if (unknownFlags.isEmpty()) return
        val optionNames = options.flatMap { listOf("--${it.long}", "-${it.short}") }
        unknownFlags.forEach { Suggestions.warnUnknownFlag(it, optionNames) }
    }

    /** Resolves each option's effective value from config, env, flags, and defaults. */
    private fun finishParsing(ctx: CommandContext, groupName: String, providedOptions: Map<String, String>) {
        ctx.config = ctx.app?.createConfigRegistry(this, groupName, providedOptions) ?: ctx.config
        val config = ctx.app?.getAppConfig()
        options.forEach { option ->
            val configKey = option.configKey ?: ConfigRegistry.defaultBindingKey(groupName, option.long)
            val registryValue = ctx.config.getString(configKey)
            val legacyValue = if (option.persistent) {
                FlagPersistence.resolveFlag(option, groupName, this.name, config)
            } else {
                null
            }
            ctx.options[option.long] = registryValue ?: legacyValue ?: option.default
        }
    }

    private companion object {
        /** A `-x` or `--xyz` token beginning with a letter (excludes `-`, `--`, `-5`). */
        val FLAG_TOKEN = Regex("^--?[a-zA-Z]")

        /** Global flags handled elsewhere; never reported as unknown by a command. */
        val GLOBAL_FLAGS = setOf("-h", "--help", "-v", "--version", "--fix")
    }
}
