package com.rkhamatyarov.laret.plugin

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Command

/**
 * Manages plugin lifecycle and execution
 */
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

    fun beforeExecute(command: Command): Boolean {
        for (plugin in plugins) {
            try {
                if (!plugin.beforeExecute(command)) return false
            } catch (e: Exception) {
                System.err.println("Exception in plugin ${plugin.name} beforeExecute: ${e.message}")
            }
        }
        return true
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
