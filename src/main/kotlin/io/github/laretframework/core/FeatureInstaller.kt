package io.github.laretframework.core

import io.github.laretframework.config.registry.ConfigLayer
import io.github.laretframework.model.CommandGroup

internal interface FeatureInstaller {
    fun beforeRun(app: CliApp) = Unit

    fun interceptDispatch(app: CliApp, args: Array<String>): Int? = null

    fun configLayers(app: CliApp): List<ConfigLayer> = emptyList()

    fun commandGroups(app: CliApp): List<CommandGroup> = emptyList()

    fun shutdown(app: CliApp) = Unit
}

internal class FeatureRegistry {
    private val installers = mutableListOf<FeatureInstaller>()

    fun install(installer: FeatureInstaller) {
        installers += installer
    }

    fun beforeRun(app: CliApp) = installers.forEach { it.beforeRun(app) }

    fun interceptDispatch(app: CliApp, args: Array<String>): Int? =
        installers.firstNotNullOfOrNull { it.interceptDispatch(app, args) }

    fun configLayers(app: CliApp): List<ConfigLayer> = installers.flatMap { it.configLayers(app) }

    fun commandGroups(app: CliApp): List<CommandGroup> = installers.flatMap { it.commandGroups(app) }

    fun shutdown(app: CliApp) = installers.forEach { it.shutdown(app) }
}
