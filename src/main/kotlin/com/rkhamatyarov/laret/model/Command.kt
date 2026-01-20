package com.rkhamatyarov.laret.model

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.core.CommandContext
import com.rkhamatyarov.laret.output.OutputStrategy
import com.rkhamatyarov.laret.output.PlainOutput
import com.rkhamatyarov.laret.ui.redBold
import org.slf4j.LoggerFactory

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
    private val log = LoggerFactory.getLogger(javaClass::class.java)

    fun execute(
        args: Array<String>,
        app: CliApp? = null,
        outputStrategy: OutputStrategy = PlainOutput,
    ) {
        val context = CommandContext(this, app, outputStrategy)

        if (app?.hasPlugins() == true) {
            if (!app.getPluginManager().beforeExecute(this)) {
                log.warn("Plugin rejected execution of command: {}", name)
                return
            }
        }

        try {
            // Parse arguments and options
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
                        // Short option
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
                        // Positional argument
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
                    log.error(redBold("Error: Required argument '${arg.name}' not provided"))
                    showHelp()
                    return
                }
            }

            action(context)
        } catch (e: Exception) {
            log.error(redBold("Error: ${e.message}"))
            e.printStackTrace()
        } finally {
            if (app?.hasPlugins() == true) {
                app.getPluginManager().afterExecute(this)
            }
        }
    }

    fun showHelp() {
        log.info("Command: $name")
        if (description.isNotEmpty()) log.info("Description: $description\n")

        if (arguments.isNotEmpty()) {
            log.info("Arguments:")
            arguments.forEach { arg ->
                val req = if (arg.required) "required" else "optional"
                log.info("  ${arg.name.padEnd(20)} $req - ${arg.description}")
            }
        }

        if (options.isNotEmpty()) {
            log.info("\nOptions:")
            options.forEach { opt ->
                val flags = "-${opt.short}, --${opt.long}".padEnd(25)
                log.info("  $flags ${opt.description}")
            }
        }
    }
}
