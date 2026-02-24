package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.config.ConfigLoader
import com.rkhamatyarov.laret.config.model.AppConfig
import com.rkhamatyarov.laret.config.validator.ConfigValidator
import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.plugin.LaretPlugin
import com.rkhamatyarov.laret.plugin.PluginManager
import org.fusesource.jansi.AnsiConsole

/**
 * Represents a complete CLI application with integrated configuration system
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

    /**
     * Initialize application with optional config file
     */
    fun init(configPath: String? = null): CliApp {
        this.configPath = configPath

        try {
            appConfig = configLoader.load(configPath)

            val validationResult = configValidator.validate(appConfig)

            if (!validationResult.isValid) {
                validationResult.errors.forEach { error ->
                    System.err.println("ERROR: $error")
                }
                throw RuntimeException("Configuration validation failed")
            }

            validationResult.warnings.forEach { warning ->
                System.err.println(" WARNING: $warning")
            }

            applyConfiguration(appConfig)

            initializePlugins()
        } catch (e: Exception) {
            throw e
        }

        return this
    }

    /**
     * Run the CLI application with arguments
     */
    fun run(args: Array<String>) {
        logManager.disableLogging()
        AnsiConsole.systemInstall()
        try {
            when {
                args.isEmpty() -> {
                    HelpFormatter.showApplicationHelp(this)
                }

                args[0] == "--help" || args[0] == "-h" -> {
                    HelpFormatter.showApplicationHelp(this)
                }

                args[0] == "--version" || args[0] == "-v" -> {
                    println("$name version $version")
                    if (appConfig.app.description.isNotEmpty()) {
                        println(appConfig.app.description)
                    }
                }

                args[0] == "--config" && args.size > 1 -> {
                    init(args[1])
                    val remainingArgs = args.drop(2).toTypedArray()
                    if (remainingArgs.isNotEmpty()) {
                        executeCommand(remainingArgs)
                    }
                }

                else -> {
                    executeCommand(args)
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
            }
            return
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

    private fun applyConfiguration(config: AppConfig) {
        if (config.output.verbose) {
            println("Verbose mode enabled")
        }

        if (config.logging.file.isNotEmpty()) {
            println("Log file configured: ${config.logging.file}")
        }

        if (config.plugins.enabled.isNotEmpty()) {
            println("Enabled plugins: ${config.plugins.enabled.joinToString(", ")}")
        }
        if (config.plugins.paths.isNotEmpty()) {
            println("Plugin search paths: ${config.plugins.paths.joinToString(", ")}")
        }
    }

    /**
     * Get loaded application configuration
     */
    fun getAppConfig(): AppConfig = appConfig

    /**
     * Get specific config section
     */
    fun getAppMetadata() = appConfig.app

    fun getOutputConfig() = appConfig.output

    fun getPluginConfig() = appConfig.plugins

    fun getLoggingConfig() = appConfig.logging

    /**
     * Save current configuration to file
     */
    fun saveConfig(outputPath: String) {
        configLoader.save(appConfig, outputPath)
        println("Configuration saved to: $outputPath")
    }

    /**
     * Reload configuration from file
     */
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
