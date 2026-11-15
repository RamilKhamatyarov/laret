package io.github.laretframework.core

import io.github.laretframework.completion.CompletionEngine
import io.github.laretframework.config.ConfigLoader
import io.github.laretframework.config.UnresolvedConfigSelection
import io.github.laretframework.config.model.AppConfig
import io.github.laretframework.config.registry.ConfigExplanation
import io.github.laretframework.config.registry.ConfigRegistry
import io.github.laretframework.config.validator.ConfigValidator
import io.github.laretframework.config.validator.ValidationResult
import io.github.laretframework.model.Command
import io.github.laretframework.model.CommandGroup
import io.github.laretframework.plugin.install.PluginInstaller
import io.github.laretframework.plugin.model.InstalledPlugin
import io.github.laretframework.plugin.model.LaretPlugin
import io.github.laretframework.plugin.runtime.PluginCatalog
import io.github.laretframework.plugin.runtime.PluginExecutor
import io.github.laretframework.plugin.runtime.PluginManager
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
    private val features = FeatureRegistry()
    private val pluginManager = PluginManager()
    private val logManager = LogManager()
    private val configLoader = ConfigLoader()
    private val configValidator = ConfigValidator()
    private var sidecarCatalog = PluginCatalog(emptyList())

    private var appConfig: AppConfig = AppConfig()
    private var configPath: String? = null
    private var configProfile: String? = null
    private var configInitialized = false
    private var configLoadError: String? = null
    private var unresolvedConfigSelection: UnresolvedConfigSelection? = null
    private var configValidation = ValidationResult(isValid = false, isKnown = false)

    /** Whether the global `--fix` flag enabled opt-in interactive typo correction. */
    private var fixMode: Boolean = false

    /**
     * Whether this app deletes `.old` binaries left by a previous self-update
     * at start-up. Off unless the app opts in, because the check locates the
     * executable and lists its directory on every invocation, which an app
     * without a self-update feature has no reason to pay for.
     */
    var cleansUpAfterSelfUpdate: Boolean = false
    var fastSignals: Boolean = false
        internal set

    internal var onInitHook: suspend (CliApp) -> Unit = {}
    internal var onShutdownHook: (suspend (CliApp) -> Unit)? = null

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

        val appHook = onShutdownHook
        if (includeAppHooks && appHook != null) scope.onShutdown { appHook(this@CliApp) }
        if (hasPlugins()) scope.onShutdown { shutdownPlugins() }
        cancellationScope = scope
        return scope
    }

    /** Initialize the application, optionally loading a config file. */
    fun init(configPath: String? = null, profile: String? = null): CliApp =
        runBlocking { initSuspending(configPath, profile) }

    internal suspend fun initSuspending(
        configPath: String? = null,
        profile: String? = null,
        diagnosticMode: Boolean = false,
    ): CliApp {
        this.configPath = configPath
        this.configProfile = profile
        configInitialized = false
        configLoadError = null
        unresolvedConfigSelection =
            if (diagnosticMode) configLoader.unresolvedSelection(configPath, profile) else null
        appConfig = try {
            configLoader.load(this.configPath, this.configProfile)
        } catch (error: RuntimeException) {
            if (!diagnosticMode) throw error
            configLoadError = error.message ?: "Configuration loading failed"
            AppConfig()
        }
        val result = configValidator.validate(appConfig)
        configValidation = result
        if (diagnosticMode && configLoadError != null) {
            configInitialized = true
            return this
        }
        if (!result.isValid) {
            if (diagnosticMode) {
                configInitialized = true
                return this
            }
            result.errors.forEach { System.err.println("ERROR: $it") }
            throw RuntimeException("Configuration validation failed")
        }
        result.warnings.forEach { System.err.println(" WARNING: $it") }
        applyConfiguration(appConfig)
        refreshSidecarPlugins()
        initializePlugins()
        onInitHook(this@CliApp)
        configInitialized = true
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
        val commandArgs = runBlocking { prepareDispatch(args) }
        features.beforeRun(this)
        val scope = startScope(includeAppHooks = true)
        val signals = if (fastSignals) FastSignals(scope).install() else null
        val hook = if (signals == null) {
            Thread({ scope.shutdown(CancellationScope.INTERRUPT_EXIT_CODE) }, "laret-signal")
                .also { Runtime.getRuntime().addShutdownHook(it) }
        } else {
            null
        }
        return try {
            runBlocking(scope.coroutineContext) { dispatchPrepared(commandArgs) }
        } catch (_: CancellationException) {
            CancellationScope.INTERRUPT_EXIT_CODE
        } finally {
            signals?.awaitIfSignalled()
            scope.shutdown(0)
            signals?.uninstall()
            hook?.let { runCatching { Runtime.getRuntime().removeShutdownHook(it) } }
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

    private suspend fun dispatch(rawArgs: Array<String>): Int = dispatchPrepared(prepareDispatch(rawArgs))

    private suspend fun prepareDispatch(rawArgs: Array<String>): Array<String> {
        if (rawArgs.any { it == FIX_FLAG }) fixMode = true
        val args = if (fixMode) rawArgs.filterNot { it == FIX_FLAG }.toTypedArray() else rawArgs
        val global = extractGlobalOptions(args)
        val parsedCheckOptions = extractConfigCheckOptions(global.remaining)
        val isConfigCheck = parsedCheckOptions.remaining.take(2) == listOf("config", "check")
        val checkOptions = if (isConfigCheck) parsedCheckOptions else global
        val commandArgs = checkOptions.remaining
        val selectedConfig = global.configPath ?: checkOptions.configPath
        val selectedProfile = global.profile ?: checkOptions.profile
        val mustInitialize = !configInitialized || selectedConfig != null || selectedProfile != null ||
            !configValidation.isKnown || !configValidation.isValid || configLoadError != null
        if (mustInitialize) {
            initSuspending(
                selectedConfig ?: configPath,
                selectedProfile ?: configProfile,
                diagnosticMode = isConfigCheck,
            )
        }
        return commandArgs
    }

    private suspend fun dispatchPrepared(commandArgs: Array<String>): Int {
        features.interceptDispatch(this, commandArgs)?.let { return it }
        if (commandArgs.firstOrNull() == CompletionEngine.COMPLETE_COMMAND) {
            print(CompletionEngine(this).complete(commandArgs.drop(1)))
            return 0
        }

        when {
            commandArgs.isEmpty() -> {
                HelpFormatter.showApplicationHelp(this)
                return 0
            }

            commandArgs[0] == "--help" || commandArgs[0] == "-h" -> {
                HelpFormatter.showApplicationHelp(this)
                return 0
            }

            commandArgs[0] == "--version" || commandArgs[0] == "-v" -> {
                println("$name version $version")
                if (appConfig.app.description.isNotEmpty()) println(appConfig.app.description)
                return 0
            }

            commandArgs[0].contains(":") -> {
                val parts = commandArgs[0].split(":", limit = 2)
                return executeCommand((parts + commandArgs.drop(1)).toTypedArray())
            }

            else -> {
                return executeCommand(commandArgs)
            }
        }
    }

    private fun applyConfiguration(@Suppress("UNUSED_PARAMETER") config: AppConfig) = Unit

    fun getAppConfig(): AppConfig = appConfig

    fun getConfigValidationResult(): ValidationResult = configValidation

    fun getConfigLoadError(): String? = configLoadError

    /** The file discovery selected for the stored path and profile, even if it could not be loaded. */
    fun getSelectedConfigFile(): String? = configLoader.selectedFile(configPath, configProfile)?.absolutePath

    /** Set only for a diagnostic check: other invocations fall back to the next candidate file. */
    fun getUnresolvedConfigSelection(): UnresolvedConfigSelection? = unresolvedConfigSelection

    fun getConfigPath(): String? = configPath

    fun getConfigProfile(): String? = configProfile

    /** Explains a configuration key for the application or a selected group command. */
    fun explainConfig(
        key: String,
        commandPath: String? = null,
        providedOptions: Map<String, String> = emptyMap(),
    ): ConfigExplanation {
        val selected = commandPath?.trim()?.takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))
        val registry = if (selected == null) {
            applicationConfigRegistry(System::getenv)
        } else {
            require(selected.size == 2) { "Command must be specified as 'group command'" }
            val group = groups.find { it.matches(selected[0]) }
                ?: throw IllegalArgumentException("Unknown command group: ${selected[0]}")
            val command = group.commands.find { it.matches(selected[1]) }
                ?: throw IllegalArgumentException("Unknown command: ${selected[0]} ${selected[1]}")
            createConfigRegistry(command, group.name, providedOptions)
        }
        return registry.explain(key)
    }

    internal fun explainConfigWithEnvironment(key: String, envProvider: () -> Map<String, String>): ConfigExplanation =
        applicationConfigRegistry(envProvider).explain(key)

    private fun applicationConfigRegistry(envProvider: () -> Map<String, String>): ConfigRegistry = ConfigRegistry()
        .defaults(applicationDefaults())
        .files(configPath = configPath, profile = configProfile)
        .env(
            bindings = mapOf(
                "log-level" to "logging.level",
                "log-file" to "logging.file",
            ),
            envProvider = envProvider,
        )

    private fun applicationDefaults(): Map<String, Any?> {
        val defaults = AppConfig()
        return mapOf(
            "app" to mapOf(
                "name" to defaults.app.name,
                "version" to defaults.app.version,
                "description" to defaults.app.description,
                "author" to defaults.app.author,
            ),
            "output" to mapOf(
                "format" to defaults.output.format,
                "colorized" to defaults.output.colorized,
                "verbose" to defaults.output.verbose,
                "prettify" to defaults.output.prettify,
            ),
            "plugins" to mapOf(
                "enabled" to defaults.plugins.enabled,
                "disabled" to defaults.plugins.disabled,
                "paths" to defaults.plugins.paths,
                "auto-load" to defaults.plugins.autoLoad,
            ),
            "logging" to mapOf(
                "level" to defaults.logging.level,
                "file" to defaults.logging.file,
                "format" to defaults.logging.format,
                "max-size" to defaults.logging.maxSize,
                "max-history" to defaults.logging.maxHistory,
            ),
            "watch" to mapOf(
                "debounce-ms" to defaults.watch.debounceMs,
                "patterns" to defaults.watch.patterns,
                "max-restarts" to defaults.watch.maxRestarts,
                "max-consecutive-failures" to defaults.watch.maxConsecutiveFailures,
            ),
        )
    }

    internal fun installFeature(installer: FeatureInstaller) {
        features.install(installer)
    }

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

        val registry = ConfigRegistry().defaults(defaults)
        if (groupName != "config" || command.name != "check") {
            registry.files(configPath = configPath, profile = configProfile)
        }
        return registry
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

    fun registerPlugin(plugin: LaretPlugin): CliApp = registerPlugins(plugin)

    fun registerPlugins(vararg plugins: LaretPlugin): CliApp {
        val hadPlugins = hasPlugins()
        plugins.forEach { pluginManager.register(it) }
        if (!hadPlugins && hasPlugins()) cancellationScope.onShutdown { shutdownPlugins() }
        return this
    }

    fun initializePlugins() {
        if (pluginManager.getPlugins().isNotEmpty()) pluginManager.initialize(this)
    }

    fun shutdown() {
        runBlocking { onShutdownHook?.invoke(this@CliApp) }
        shutdownPlugins()
    }

    internal fun shutdownPlugins() {
        if (pluginManager.getPlugins().isNotEmpty()) pluginManager.shutdown()
    }

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

                args[index].startsWith("--config=") -> {
                    nextConfigPath = args[index].substringAfter('=')
                    index++
                }

                args[index] == "--profile" && index + 1 < args.size -> {
                    nextProfile = args[index + 1]
                    index += 2
                }

                args[index].startsWith("--profile=") -> {
                    nextProfile = args[index].substringAfter('=')
                    index++
                }

                else -> {
                    remaining.add(args[index])
                    index++
                }
            }
        }

        return GlobalOptions(nextConfigPath, nextProfile, remaining.toTypedArray())
    }

    private fun extractConfigCheckOptions(args: Array<String>): GlobalOptions {
        var config: String? = null
        var profile: String? = null
        val remaining = mutableListOf<String>()
        var index = 0
        while (index < args.size) {
            when {
                args[index] in setOf("-c", "--config") && index + 1 < args.size -> {
                    config = args[index + 1]
                    index += 2
                }

                args[index].startsWith("--config=") -> {
                    config = args[index].substringAfter('=')
                    index++
                }

                args[index] in setOf("-p", "--profile") && index + 1 < args.size -> {
                    profile = args[index + 1]
                    index += 2
                }

                args[index].startsWith("--profile=") -> {
                    profile = args[index].substringAfter('=')
                    index++
                }

                else -> remaining += args[index++]
            }
        }
        return GlobalOptions(config, profile, remaining.toTypedArray())
    }

    private companion object {
        const val FIX_FLAG = "--fix"
    }
}
