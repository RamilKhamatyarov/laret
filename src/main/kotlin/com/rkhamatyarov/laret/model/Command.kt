package com.rkhamatyarov.laret.model

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.core.HelpFormatter
import com.rkhamatyarov.laret.output.OutputStrategy
import com.rkhamatyarov.laret.output.PlainOutput
import com.rkhamatyarov.laret.ui.redBold

/**
 * Represents a single command
 */
data class Command(
    val name: String,
    val description: String = "",
    val arguments: List<Argument> = emptyList(),
    val options: List<Option> = emptyList(),
    val action: (CommandContext) -> Unit = {},
) {
    fun execute(
        args: Array<String>,
        app: CliApp? = null,
        outputStrategy: OutputStrategy = PlainOutput,
    ) {
        val context = CommandContext(this, app, outputStrategy)

        if (app?.hasPlugins() == true) {
            if (!app.getPluginManager().beforeExecute(this)) {
                println("Plugin rejected execution of command: $name")
                return
            }
        }

        try {
            var argIndex = 0
            var i = 0

            while (i < args.size) {
                val arg = args[i]
                when {
                    arg.startsWith("--") -> {
                        // Long option
                        val optName = arg.substring(2)
                        val opt = options.find { it.long == optName }
                        if (opt != null) {
                            if (opt.takesValue && i + 1 < args.size && !args[i + 1].startsWith("-")) {
                                context.options[opt.long] = args[i + 1]
                                i++
                            } else {
                                context.options[opt.long] = "true"
                            }
                        }
                    }

                    arg.startsWith("-") && arg.length == 2 -> {
                        val shortName = arg.substring(1)
                        val opt = options.find { it.short == shortName }

                        if (opt != null) {
                            if (opt.takesValue && i + 1 < args.size && !args[i + 1].startsWith("-")) {
                                context.options[opt.long] = args[i + 1]
                                i++
                            } else {
                                context.options[opt.long] = "true"
                            }
                        }
                    }

                    !arg.startsWith("-") -> {
                        if (argIndex < arguments.size) {
                            context.arguments[arguments[argIndex].name] = arg
                            argIndex++
                        }
                    }
                }

                i++
            }

            arguments.filter { it.required && !it.optional }.forEach { arg ->
                if (!context.arguments.containsKey(arg.name)) {
                    HelpFormatter.showArgumentMissingError(arg.name)
                    HelpFormatter.showCommandHelp(this)
                    return
                }
            }

            action(context)
        } catch (e: Exception) {
            println(redBold("Error: ${e.message}"))
            e.printStackTrace()
        } finally {
            if (app?.hasPlugins() == true) {
                app.getPluginManager().afterExecute(this)
            }
        }
    }
}
