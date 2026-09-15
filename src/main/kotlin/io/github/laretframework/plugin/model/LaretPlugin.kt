package io.github.laretframework.plugin.model

import io.github.laretframework.core.CliApp
import io.github.laretframework.model.Command

/** Base interface for in-process Laret plugins. */
interface LaretPlugin {
    val name: String
    val version: String
        get() = "1.0.0"

    fun initialize(app: CliApp) {}

    fun beforeExecute(command: Command): Boolean = true

    fun afterExecute(command: Command) {}

    fun shutdown() {}
}
