package com.rkhamatyarov.laret.plugin.runtime

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.plugin.model.LaretPlugin

/** Manages in-process plugin lifecycle. Sidecars are handled by [PluginCatalog]. */
class PluginManager {
    private val plugins = mutableListOf<LaretPlugin>()

    fun register(plugin: LaretPlugin): PluginManager {
        plugins.add(plugin)
        return this
    }

    fun initialize(app: CliApp) {
        plugins.forEach { plugin ->
            try {
                plugin.initialize(app)
            } catch (e: Exception) {
                System.err.println("Failed to initialize plugin ${plugin.name}: ${e.message}")
            }
        }
    }

    fun beforeExecute(command: Command): Boolean = plugins.all { plugin ->
        try {
            plugin.beforeExecute(command)
        } catch (e: Exception) {
            System.err.println("Exception in plugin ${plugin.name} beforeExecute: ${e.message}")
            true
        }
    }

    fun afterExecute(command: Command) {
        plugins.forEach { plugin ->
            try {
                plugin.afterExecute(command)
            } catch (e: Exception) {
                System.err.println("Exception in plugin ${plugin.name} afterExecute: ${e.message}")
            }
        }
    }

    fun shutdown() {
        plugins.forEach { plugin ->
            try {
                plugin.shutdown()
            } catch (e: Exception) {
                System.err.println("Exception in plugin ${plugin.name} shutdown: ${e.message}")
            }
        }
    }

    fun getPlugins(): List<LaretPlugin> = plugins.toList()
}
