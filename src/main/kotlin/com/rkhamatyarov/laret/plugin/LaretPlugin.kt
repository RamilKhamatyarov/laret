package com.rkhamatyarov.laret.plugin

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Command

/**
 * Base interface for Laret plugins
 */
interface LaretPlugin {
    val name: String
    val version: String
        get() = "1.0.0"

    /**
     * Called when plugin is registered
     */
    fun initialize(app: CliApp) {}

    /**
     * Called before command execution
     */
    fun beforeExecute(command: Command): Boolean = true

    /**
     * Called after command execution
     */
    fun afterExecute(command: Command) {}

    /**
     * Called on plugin shutdown
     */
    fun shutdown() {}
}
