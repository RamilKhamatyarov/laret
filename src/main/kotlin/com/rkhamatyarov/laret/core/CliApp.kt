package com.rkhamatyarov.laret.core

import com.rkhamatyarov.laret.completion.CompletionEngine
import com.rkhamatyarov.laret.config.ConfigLoader
import com.rkhamatyarov.laret.config.model.AppConfig
import com.rkhamatyarov.laret.config.registry.ConfigRegistry
import com.rkhamatyarov.laret.config.validator.ConfigValidator
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.plugin.install.PluginInstaller
import com.rkhamatyarov.laret.plugin.model.InstalledPlugin
import com.rkhamatyarov.laret.plugin.model.LaretPlugin
import com.rkhamatyarov.laret.plugin.runtime.PluginCatalog
import com.rkhamatyarov.laret.plugin.runtime.PluginExecutor
import com.rkhamatyarov.laret.plugin.runtime.PluginManager
import com.rkhamatyarov.laret.update.OldBinaryCleaner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

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
    val middlewares: MiddlewareRegistry = MiddlewareRegistry.EMPTY,
) {
    private val pluginManager = PluginManager()
    private val logManager = LogManager()
    private val configLoader = ConfigLoader()
    private val configValidator = ConfigValidator()
    private var sidecarCatalog = PluginCatalog(emptyList())

    private var appConfig: AppConfig = AppConfig()
    private var configPath: String? = null
    private var configProfile: String? = null

    /** Whether the global `--fix` flag enabled opt-in interactive typo correction. */
    private var fixMode: Boolean = false

    internal var onInitHook: suspend (CliApp) -> Unit = {}
    internal var onShutdownHook: suspend (CliApp) -> Unit = {}

    /**
     * The current run's cancellation scope. Recreated per run so cancelling its
     * job never leaks between runs; commands reach it via [CommandContext.scope].
     */
    @Volatile
    internal var cancellationScope: CancellationScope = CancellationScope()
        private set

    /** Builds a fresh scope for a run and registers the outermost framework cleanups. */
    private fun startScope(includeAppHooks: Boolean): CancellationScope {
        val scope = CancellationScope()

        if (includeAppHooks) scope.onShutdown { onShutdownHook(this@CliApp) }
        scope.onShutdown { shutdownPlugins() }
        cancellationScope = scope
        return scope
    }

    /** Initialize the application, optionally loading a config file. */
    fun init(configPath: String? = null, profile: String? = null): CliApp =
        runBlocking { initSuspending(configPath, profile) }

    internal suspend fun initSuspending(configPath: String? = null, profile: String? = null): CliApp {
        this.configPath = configPath
        this.configProfile = profile
        appConfig = configLoader.load(configPath, profile)
        val result = configValidator.validate(appConfig)
        if (!result.isValid) {
            result.errors.forEach { System.err.println("ERROR: $it") }
            throw RuntimeException("Configuration validation failed")
        }
        result.warnings.forEach { System.err.println(" WARNING: $it") }
        applyConfiguration(appConfig)
        refreshSidecarPlugins()
        initializePlugins()
        onInitHook(this@CliApp)
        return this
    }

    private suspend fun executeCommand(args: Array<String>): Int {
        val groupInput = args.getOrNull(0) ?: return 0

        if (args.size == 2 && (args[1] == "-h" || args[1] == "--help")) {
            val group = groups.find { it.matches(groupInput) }
            if (group != null) {
                HelpFormatter.showGroupHelp(group)
                return 0
            }
        }

        val group = groups.find { it.matches(groupInput) }
            ?: run {
                val pluginArgs = args.drop(1)
                if (sidecarCatalog.findActive(groupInput) != null) {
                    val dryRun = pluginArgs.any { it == "--dry-run" }
                    val forwarded = pluginArgs.filterNot { it == "--dry-run" }
                    return PluginExecutor(sidecarCatalog).execute(groupInput, forwarded, dryRun, configProfile)
                }
                val groupNames = groups.map { it.name }
                if (fixMode) {
                    Suggestions.promptFix(groupInput, groupNames)?.let { corrected ->
                        return executeCommand((listOf(corrected) + args.drop(1)).toTypedArray())
                    }
                }
                println("Group not found: $groupInput")
                Suggestions.didYouMean(groupInput, groupNames)?.let { System.err.println(it) }
                HelpFormatter.showApplicationHelp(this)
                return 1
            }

        val commandInput = args.getOrNull(1) ?: return 0
        val cmdArgs = args.drop(2).toTypedArray()

        val command = group.commands.find { it.matches(commandInput) }
            ?: run {
                val commandNames = group.commands.filterNot { it.hidden }.map { it.name }
                if (fixMode) {
                    Suggestions.promptFix(commandInput, commandNames)?.let { corrected ->
                        val fixed = group.commands.first { it.name == corrected }
                        return CommandRunner.executeCommand(fixed, cmdArgs, this@CliApp, group.name)
                    }
                }
                HelpFormatter.showCommandNotFound(commandInput, group)
                Suggestions.didYouMean(commandInput, commandNames)?.let { System.err.println(it) }
                return 1
            }

        return CommandRunner.executeCommand(command, cmdArgs, this@CliApp, group.name)
    }

    /**
     * Run the CLI with the supplied argument array.
     *
     * laret writes raw ANSI escapes (gated by `Colors.isColorSupported()`) and
     * lets the terminal render them, which is correct for modern terminals
     * (WezTerm, Windows Terminal, ConPTY, and Unix). See the
     * `colored-output-native-image-and-detection` ADR for why the previous
     * Jansi console wrapper was removed.
     */
    fun run(args: Array<String>): Int {
        logManager.disableLogging()
        OldBinaryCleaner.cleanupSilently()
        val scope = startScope(includeAppHooks = true)
        val hook = Thread({ scope.shutdown(CancellationScope.INTERRUPT_EXIT_CODE) }, "laret-signal")
        Runtime.getRuntime().addShutdownHook(hook)
        return try {
            runBlocking(scope.coroutineContext) { dispatch(args) }
        } catch (_: CancellationException) {
            CancellationScope.INTERRUPT_EXIT_CODE
        } finally {
            scope.shutdown(0)
            runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
        }
    }

    /**
     * Run the CLI, preserving any [System.setOut] / [System.setErr] streams that
     * unit tests have installed so [println] output is captured correctly.
     * Behaves like [run]; kept as the explicit test entry point.
     */
    fun runForTest(args: Array<String>): Int {
        logManager.disableLogging()
        val scope = startScope(includeAppHooks = false)
        return try {
            runBlocking(scope.coroutineContext) { dispatch(args) }
        } catch (_: CancellationException) {
            CancellationScope.INTERRUPT_EXIT_CODE
        } finally {
            scope.shutdown(0)
        }
    }

    /** Suspending test entry point that cooperates with the caller's coroutine scheduler. */
    internal suspend fun runForTestSuspending(args: Array<String>): Int {
        logManager.disableLogging()
        val scope = startScope(includeAppHooks = false)
        return try {
            dispatch(args)
        } finally {
            scope.shutdown(0)
        }
    }

    private suspend fun dispatch(rawArgs: Array<String>): Int {
        if (rawArgs.firstOrNull() == CompletionEngine.COMPLETE_COMMAND) {
            print(CompletionEngine(this).complete(rawArgs.drop(1)))
            return 0
        }

        if (rawArgs.any { it == FIX_FLAG }) fixMode = true
        val args = if (fixMode) rawArgs.filterNot { it == FIX_FLAG }.toTypedArray() else rawArgs

        val global = extractGlobalOptions(args)
        if (global.configPath != null || global.profile != null) {
            initSuspending(global.configPath ?: configPath, global.profile ?: configProfile)
            return dispatch(global.remaining)
        }

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

            args[0].contains(":") -> {
                val parts = args[0].split(":", limit = 2)
                return executeCommand((parts + args.drop(1)).toTypedArray())
            }

            args[0] == "--config" && args.size > 1 -> {
                initSuspending(args[1])
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

    private fun applyConfiguration(@Suppress("UNUSED_PARAMETER") config: AppConfig) = Unit

    fun getAppConfig(): AppConfig = appConfig

    internal fun createConfigRegistry(
        command: Command,
        groupName: String,
        providedOptions: Map<String, String>,
    ): ConfigRegistry {
        val bindings = command.options.associate { option ->
            option.long to (option.configKey ?: ConfigRegistry.defaultBindingKey(groupName, option.long))
        }
        val defaults = command.options
            .filterNot { it.persistent }
            .associate { option ->
                (option.configKey ?: ConfigRegistry.defaultBindingKey(groupName, option.long)) to option.default
            }

        return ConfigRegistry()
            .defaults(defaults)
            .files(configPath = configPath, profile = configProfile)
            .env(prefix = "LARET", bindings = bindings)
            .flags(values = providedOptions, bindings = bindings)
    }

    fun getAppMetadata() = appConfig.app

    fun getOutputConfig() = appConfig.output

    fun getPluginConfig() = appConfig.plugins
    fun getSidecarPlugins(): List<InstalledPlugin> = sidecarCatalog.list()

    fun refreshSidecarPlugins() {
        val reserved = groups.map { it.name }.toMutableSet()
        reserved += setOf("plugin", "help", "version", "middleware")
        val directories = PluginCatalog.directories(appConfig.plugins.paths)
        sidecarCatalog = PluginCatalog(directories, appConfig.plugins, reserved)
        sidecarCatalog.refresh()
    }

    fun pluginDirectories(override: Path? = null): List<Path> =
        PluginCatalog.directories(appConfig.plugins.paths, override)

    fun installSidecarPlugin(
        name: String,
        url: String,
        sha256: String,
        directory: Path,
        force: Boolean = false,
    ): Result<Path> = PluginInstaller().install(name, url, sha256, directory, force)

    fun removeSidecarPlugin(name: String, force: Boolean = false): Result<Unit> = sidecarCatalog.remove(name, force)

    fun getLoggingConfig() = appConfig.logging

    fun getWatchConfig() = appConfig.watch

    fun saveConfig(outputPath: String) {
        configLoader.save(appConfig, outputPath)
    }

    fun reloadConfig(): CliApp {
        appConfig = configLoader.load(configPath, configProfile)
        applyConfiguration(appConfig)
        refreshSidecarPlugins()
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

    private data class GlobalOptions(val configPath: String?, val profile: String?, val remaining: Array<String>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as GlobalOptions

            if (configPath != other.configPath) return false
            if (profile != other.profile) return false
            if (!remaining.contentEquals(other.remaining)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = configPath?.hashCode() ?: 0
            result = 31 * result + (profile?.hashCode() ?: 0)
            result = 31 * result + remaining.contentHashCode()
            return result
        }
    }

    private fun extractGlobalOptions(args: Array<String>): GlobalOptions {
        var nextConfigPath: String? = null
        var nextProfile: String? = null
        val remaining = mutableListOf<String>()
        var index = 0

        while (index < args.size) {
            when {
                args[index] == "--config" && index + 1 < args.size -> {
                    nextConfigPath = args[index + 1]
                    index += 2
                }

                args[index] == "--profile" && index + 1 < args.size -> {
                    nextProfile = args[index + 1]
                    index += 2
                }

                else -> {
                    remaining.add(args[index])
                    index++
                }
            }
        }

        return GlobalOptions(nextConfigPath, nextProfile, remaining.toTypedArray())
    }

    private companion object {
        const val FIX_FLAG = "--fix"
    }
}
