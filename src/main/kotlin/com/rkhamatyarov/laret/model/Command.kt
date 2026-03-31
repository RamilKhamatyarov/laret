package com.rkhamatyarov.laret.model

import com.rkhamatyarov.laret.core.CliApp
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
    val action: (CommandContext) -> Unit = {}
) {
    /** True when [input] equals the primary name or any alias. */
    fun matches(input: String): Boolean = input == name || input in aliases

    /**
     * Execute this command.
     *
     * Option resolution order (first match wins):
     *  1. Value explicitly supplied on the command line.
     *  2. Config-file override from [AppConfig.flags] (only for options with
     *     `persistent = true` that were **not** supplied on the CLI).
     *  3. Compile-time [Option.default].
     *
     * @param args      Raw arguments after the command token (positional + flags).
     * @param app       The running [CliApp] — used to access the loaded config.
     * @param groupName Name of the group this command belongs to, used to build
     *                  the config-key hierarchy for persistent-flag lookup.
     */
    fun execute(args: Array<String>, app: CliApp? = null, groupName: String = "") {
        val providedOptions = mutableMapOf<String, String>()

        val ctx = CommandContext(this, app, groupName = groupName)

        val positional = args.filter { !it.startsWith("-") }
        arguments.forEachIndexed { idx, arg ->
            ctx.arguments[arg.name] = positional.getOrElse(idx) { arg.default }
        }

        val finalOptions = mutableMapOf<String, String>()

        options.forEach { option ->
            val cliValue = providedOptions[option.long]
            if (cliValue != null) {
                finalOptions[option.long] = cliValue
            } else if (option.persistent) {
                val configValue =
                    FlagPersistence.resolveFlag(
                        option = option,
                        groupName = groupName,
                        commandName = this.name,
                        config = app?.getAppConfig()
                    )
                if (configValue != null) {
                    finalOptions[option.long] = configValue
                } else {
                    finalOptions[option.long] = option.default
                }
            } else {
                finalOptions[option.long] = option.default
            }
        }

        val explicitlySupplied = mutableSetOf<String>()
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
                explicitlySupplied.add(opt.long)
            } else {
                i++
            }
        }

        val config = app?.getAppConfig()
        options
            .filter { it.persistent && it.long !in explicitlySupplied }
            .forEach { opt ->
                val resolved = FlagPersistence.resolveFlag(opt, groupName, name, config)
                if (resolved != null) {
                    ctx.options[opt.long] = resolved
                }
            }

        action(ctx)
    }
}
