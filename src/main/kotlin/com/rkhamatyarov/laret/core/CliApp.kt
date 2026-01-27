package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.plugin.LaretPlugin
import com.rkhamatyarov.laret.plugin.PluginManager
import org.fusesource.jansi.AnsiConsole

/**
 * Represents a complete CLI application
 */
data class CliApp(
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val groups: List<CommandGroup> = emptyList(),
) {
    private val pluginManager = PluginManager()
    private val logManager = LogManager()
    private var quietMode = false

    fun run(args: Array<String>) {
        AnsiConsole.systemInstall()
        try {
            quietMode = true
            logManager.disableLogging()
            when {
                args.isEmpty() -> {
                    HelpFormatter.showApplicationHelp(this)
                }

                args[0] == "--help" || args[0] == "-h" -> {
                    HelpFormatter.showApplicationHelp(this)
                }

                args[0] == "--version" || args[0] == "-v" -> {
                    println("$name version $version")
                }

                else -> {
                    val filteredArgs = args.filter { it != "--quiet" }.toTypedArray()
                    executeCommand(filteredArgs)
                }
            }
        } finally {
            shutdownPlugins()
            AnsiConsole.systemUninstall()
        }
    }

    private fun executeCommand(args: Array<String>) {
        val groupName = args.getOrNull(0) ?: return
        if (args.size == 2 && (args[1] == "-h" || args[1] == "--help")) {
            val group = groups.find { it.name == groupName }
            if (group != null) {
                HelpFormatter.showGroupHelp(group)
                return
            }
        }

        val commandName = args.getOrNull(1) ?: return
        val cmdArgs = args.drop(2).toTypedArray()
        val group =
            groups.find { it.name == groupName }
                ?: run {
                    println("Group not found: $groupName")
                    HelpFormatter.showApplicationHelp(this)
                    return
                }

        val command =
            group.commands.find { it.name == commandName }
                ?: run {
                    HelpFormatter.showCommandNotFound(commandName, group)
                    return
                }

        command.execute(cmdArgs, this)
    }

    fun isQuietMode(): Boolean = quietMode

    fun registerPlugin(plugin: LaretPlugin): CliApp {
        pluginManager.register(plugin)
        return this
    }

    fun registerPlugins(vararg plugins: LaretPlugin): CliApp {
        plugins.forEach { pluginManager.register(it) }
        return this
    }

    fun initializePlugins() {
        if (pluginManager.getPlugins().isNotEmpty()) {
            pluginManager.initialize(this)
        }
    }

    fun shutdownPlugins() {
        if (pluginManager.getPlugins().isNotEmpty()) {
            pluginManager.shutdown()
        }
    }

    internal fun getPluginManager(): PluginManager = pluginManager

    fun hasPlugins(): Boolean = pluginManager.getPlugins().isNotEmpty()

    fun getPlugins(): List<LaretPlugin> = pluginManager.getPlugins()

    fun findPlugin(name: String): LaretPlugin? = pluginManager.getPlugins().find { it.name == name }
}
