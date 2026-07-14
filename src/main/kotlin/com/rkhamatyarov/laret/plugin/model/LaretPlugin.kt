package com.rkhamatyarov.laret.plugin.model

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Command

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
