package com.rkhamatyarov.laret.dsl

import com.rkhamatyarov.laret.core.CliApp

/**
 * Main DSL entry point - creates a CLI application
 */
fun cli(
    name: String,
    version: String = "1.0.0",
    description: String = "",
    block: CliBuilder.() -> Unit,
): CliApp {
    val builder = CliBuilder(name, version, description)
    builder.block()
    return builder.build()
}
