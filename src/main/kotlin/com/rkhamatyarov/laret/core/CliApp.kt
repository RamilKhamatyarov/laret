package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.config.ConfigLoader
import com.rkhamatyarov.laret.config.model.AppConfig
import com.rkhamatyarov.laret.config.validator.ConfigValidator
import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.plugin.LaretPlugin
import com.rkhamatyarov.laret.plugin.PluginManager
import kotlinx.coroutines.runBlocking
import org.fusesource.jansi.AnsiConsole

/**
 * Represents a complete CLI application.
 *
 * Both groups and commands support *aliases* — alternative short names resolved
 * transparently at runtime:
 * ```
 * laret file create …   # primary names
 * laret f    create …   # group alias
 * laret file c      …   # command alias
 * laret f    c      …   # both aliases combined
 * ```
 */
data class CliApp(
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val groups: List<CommandGroup> = emptyList(),
) {
    private val pluginManager = PluginManager()
    private val logManager = LogManager()
    private val configLoader = ConfigLoader()
    private val configValidator = ConfigValidator()

    private var appConfig: AppConfig = AppConfig()
    private var configPath: String? = null

    internal var onInitHook: suspend (CliApp) -> Unit = {}
    internal var onShutdownHook: suspend (CliApp) -> Unit = {}

    /** Initialize the application, optionally loading a config file. */
    fun init(configPath: String? = null): CliApp {
        this.configPath = configPath
        try {
            appConfig = configLoader.load(configPath)
            val result = configValidator.validate(appConfig)
            if (!result.isValid) {
                result.errors.forEach { System.err.println("ERROR: $it") }
                throw RuntimeException("Configuration validation failed")
            }
            result.warnings.forEach { System.err.println(" WARNING: $it") }
            applyConfiguration(appConfig)
            initializePlugins()
        } catch (e: Exception) {
            throw e
        }
        runBlocking { onInitHook(this@CliApp) }
        return this
    }

    private fun executeCommand(args: Array<String>): Int {
        val groupInput = args.getOrNull(0) ?: return 0

        if (args.size == 2 && (args[1] == "-h" || args[1] == "--help")) {
            val group = groups.find { it.matches(groupInput) }
            if (group != null) {
                HelpFormatter.showGroupHelp(group)
                return 0
            }
        }

        val commandInput = args.getOrNull(1) ?: return 0
        val cmdArgs = args.drop(2).toTypedArray()

        val group = groups.find { it.matches(groupInput) }
            ?: run {
                println("Group not found: $groupInput")
                HelpFormatter.showApplicationHelp(this)
                return 1
            }

        val command = group.commands.find { it.matches(commandInput) }
            ?: run {
                HelpFormatter.showCommandNotFound(commandInput, group)
                return 1
            }

        return runBlocking {
            CommandRunner.executeCommand(command, cmdArgs, this@CliApp, group.name)
        }
    }

    /**
     * Run the CLI with the supplied argument array.
     *
     * Installs Jansi's ANSI console wrappers around [System.out] / [System.err]
     * for rich terminal output.  Use [runForTest] in unit tests so that
     * [System.setOut] / [System.setErr] capture streams are not overridden.
     */
    fun run(args: Array<String>): Int {
        logManager.disableLogging()
        AnsiConsole.systemInstall()
        try {
            return dispatch(args)
        } finally {
            shutdownPlugins()
            AnsiConsole.systemUninstall()
        }
    }

    /**
     * Run the CLI **without** installing Jansi's console wrappers.
     *
     * This preserves any [System.setOut] / [System.setErr] streams that unit
     * tests have installed, so [println] output is captured correctly.
     * Do **not** call this from production code — use [run] instead.
     */
    fun runForTest(args: Array<String>): Int {
        logManager.disableLogging()
        val exitCode = dispatch(args)
        shutdownPlugins()
        return exitCode
    }

    private fun dispatch(args: Array<String>): Int {
        when {
            args.isEmpty() -> {
                HelpFormatter.showApplicationHelp(this)
                return 0
            }

            args[0] == "--help" || args[0] == "-h" -> {
                HelpFormatter.showApplicationHelp(this)
                return 0
            }

            args[0] == "--version" || args[0] == "-v" -> {
                println("$name version $version")
                if (appConfig.app.description.isNotEmpty()) println(appConfig.app.description)
                return 0
            }

            args[0] == "--config" && args.size > 1 -> {
                init(args[1])
                val remaining = args.drop(2).toTypedArray()
                return if (remaining.isNotEmpty()) {
                    executeCommand(remaining)
                } else {
                    0
                }
            }

            else -> {
                return executeCommand(args)
            }
        }
    }

    private fun applyConfiguration(config: AppConfig) {
        if (config.output.verbose) println("Verbose mode enabled")
        if (config.logging.file.isNotEmpty()) println("Log file configured: ${config.logging.file}")
        if (config.plugins.enabled.isNotEmpty()) {
            println("Enabled plugins: ${config.plugins.enabled.joinToString(", ")}")
        }
        if (config.plugins.paths.isNotEmpty()) {
            println("Plugin search paths: ${config.plugins.paths.joinToString(", ")}")
        }
    }

    fun getAppConfig(): AppConfig = appConfig

    fun getAppMetadata() = appConfig.app

    fun getOutputConfig() = appConfig.output

    fun getPluginConfig() = appConfig.plugins

    fun getLoggingConfig() = appConfig.logging

    fun saveConfig(outputPath: String) {
        configLoader.save(appConfig, outputPath)
        println("Configuration saved to: $outputPath")
    }

    fun reloadConfig(): CliApp {
        appConfig = configLoader.load(configPath)
        applyConfiguration(appConfig)
        println("Configuration reloaded")
        return this
    }

    fun registerPlugin(plugin: LaretPlugin): CliApp {
        pluginManager.register(plugin)
        return this
    }

    fun registerPlugins(vararg plugins: LaretPlugin): CliApp {
        plugins.forEach { pluginManager.register(it) }
        return this
    }

    fun initializePlugins() {
        if (pluginManager.getPlugins().isNotEmpty()) pluginManager.initialize(this)
    }

    fun shutdown() {
        runBlocking { onShutdownHook(this@CliApp) }
        shutdownPlugins()
    }

    internal fun shutdownPlugins() {
        if (pluginManager.getPlugins().isNotEmpty()) pluginManager.shutdown()
    }

    internal fun getPluginManager(): PluginManager = pluginManager

    fun hasPlugins(): Boolean = pluginManager.getPlugins().isNotEmpty()

    fun getPlugins(): List<LaretPlugin> = pluginManager.getPlugins()

    fun findPlugin(name: String): LaretPlugin? = pluginManager.getPlugins().find { it.name == name }
}
