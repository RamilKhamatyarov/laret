package com.rkhamatyarov.laret.core

import ch.qos.logback.classic.Level
import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.plugin.LaretPlugin
import com.rkhamatyarov.laret.plugin.PluginManager
import com.rkhamatyarov.laret.ui.redBold
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.AnsiConsole
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("laret.core")

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
    private var quietMode = false

    fun run(args: Array<String>) {
        AnsiConsole.systemInstall()

        try {
            quietMode = args.contains("--quiet") || isCompletionCommand(args)

            if (quietMode) {
                disableLogging()
            }

            when {
                args.isEmpty() -> {
                    showHelp()
                }

                args[0] == "--help" || args[0] == "-h" -> {
                    showHelp()
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

    private fun isCompletionCommand(args: Array<String>): Boolean = args.getOrNull(0) == "completion"

    private fun executeCommand(args: Array<String>) {
        val groupName = args.getOrNull(0) ?: return

        if (args.size == 2 && (args[1] == "-h" || args[1] == "--help")) {
            val group = groups.find { it.name == groupName }
            if (group != null) {
                group.showHelp()
                return
            }
        }

        val commandName = args.getOrNull(1) ?: return
        val cmdArgs = args.drop(2).toTypedArray()

        val group =
            groups.find { it.name == groupName }
                ?: run {
                    log.error("Group not found: {}", groupName)
                    println(redBold("❌ Group not found: $groupName"))
                    showHelp()
                    return
                }

        val command =
            group.commands.find { it.name == commandName }
                ?: run {
                    log.error("Command not found: {} in group {}", commandName, groupName)
                    println(redBold("❌ Command not found: $commandName"))
                    group.showHelp()
                    return
                }

        command.execute(cmdArgs, this)
    }

    fun showHelp() {
        println(
            """
            ${Ansi.ansi().bold().fg(Ansi.Color.CYAN)}========================================${Ansi.ansi().reset()}
            ${Ansi.ansi().bold().fg(Ansi.Color.CYAN)}$name v$version${Ansi.ansi().reset()}
            $description
            ${Ansi.ansi().bold().fg(Ansi.Color.CYAN)}========================================${Ansi.ansi().reset()}
            
            ${Ansi.ansi().bold()}USAGE:${Ansi.ansi().reset()}
              $name [COMMAND] [SUBCOMMAND] [OPTIONS]
            
            ${Ansi.ansi().bold()}COMMANDS:${Ansi.ansi().reset()}
              ${Ansi.ansi().fg(Ansi.Color.GREEN)}file${Ansi.ansi().reset()}                 File operations
                ${Ansi.ansi().fg(Ansi.Color.BLUE)}create${Ansi.ansi().reset()}             Create a new file
                ${Ansi.ansi().fg(Ansi.Color.BLUE)}delete${Ansi.ansi().reset()}             Delete a file
                ${Ansi.ansi().fg(Ansi.Color.BLUE)}read${Ansi.ansi().reset()}               Read file contents
                
              ${Ansi.ansi().fg(Ansi.Color.GREEN)}dir${Ansi.ansi().reset()}                  Directory operations
                ${Ansi.ansi().fg(Ansi.Color.BLUE)}list${Ansi.ansi().reset()}               List directory contents
                ${Ansi.ansi().fg(Ansi.Color.BLUE)}create${Ansi.ansi().reset()}             Create a new directory
            
            ${Ansi.ansi().bold()}GLOBAL OPTIONS:${Ansi.ansi().reset()}
              -h, --help              Show this help message
              -v, --version           Show version
              --quiet                 Suppress all logging output
            
            ${Ansi.ansi().bold()}EXAMPLES:${Ansi.ansi().reset()}
              $name file create /tmp/test.txt --content "hello"
              $name dir list . --long --all
              $name file read /tmp/test.txt --quiet
              $name completion bash > completion.sh
            
            For more information on a command, use:
              $name [COMMAND] --help
            """.trimIndent(),
        )
    }

    private fun disableLogging() {
        val loggerContext = LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext
        loggerContext?.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)?.level = Level.OFF
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
        if (pluginManager.getPlugins().isNotEmpty() && !quietMode) {
            log.info("Initializing ${pluginManager.getPlugins().size} plugin(s)")
            pluginManager.initialize(this)
        }
    }

    fun shutdownPlugins() {
        if (pluginManager.getPlugins().isNotEmpty() && !quietMode) {
            log.info("Shutting down ${pluginManager.getPlugins().size} plugin(s)")
            pluginManager.shutdown()
        }
    }

    internal fun getPluginManager(): PluginManager = pluginManager

    fun hasPlugins(): Boolean = pluginManager.getPlugins().isNotEmpty()

    fun getPlugins(): List<LaretPlugin> = pluginManager.getPlugins()

    fun findPlugin(name: String): LaretPlugin? = pluginManager.getPlugins().find { it.name == name }
}
