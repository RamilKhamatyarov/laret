package com.rkhamatyarov.laret.plugin

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Command
import org.slf4j.LoggerFactory

/**
 * Manages plugin lifecycle and execution
 */
class PluginManager {
    private val log = LoggerFactory.getLogger(javaClass)
    private val plugins = mutableListOf<LaretPlugin>()

    fun register(plugin: LaretPlugin): PluginManager {
        plugins.add(plugin)
        log.info("Plugin registered: ${plugin.name} v${plugin.version}")
        return this
    }

    fun initialize(app: CliApp) {
        plugins.forEach { plugin ->
            try {
                plugin.initialize(app)
                log.info("Plugin initialized: ${plugin.name}")
            } catch (e: Exception) {
                log.error("Failed to initialize plugin ${plugin.name}", e)
            }
        }
    }

    fun beforeExecute(command: Command): Boolean {
        for (plugin in plugins) {
            try {
                if (!plugin.beforeExecute(command)) {
                    log.warn("Plugin ${plugin.name} rejected execution of ${command.name}")
                    return false
                }
            } catch (e: Exception) {
                log.error("Exception in plugin ${plugin.name} beforeExecute: ${e.message}", e)
                continue
            }
        }
        return true
    }

    fun afterExecute(command: Command) {
        plugins.forEach { plugin ->
            try {
                plugin.afterExecute(command)
            } catch (e: Exception) {
                log.error("Exception in plugin ${plugin.name} afterExecute: ${e.message}", e)
            }
        }
    }

    fun shutdown() {
        plugins.forEach { plugin ->
            try {
                plugin.shutdown()
            } catch (e: Exception) {
                log.error("Exception in plugin ${plugin.name} shutdown: ${e.message}", e)
            }
        }
    }

    fun getPlugins(): List<LaretPlugin> = plugins.toList()
}
