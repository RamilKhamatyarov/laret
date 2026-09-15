package io.github.laretframework.dsl

import io.github.laretframework.core.CliApp

/** Main DSL entry point - creates a CLI application */
fun cli(name: String, version: String = "1.0.0", description: String = "", block: CliBuilder.() -> Unit): CliApp {
    val builder = CliBuilder(name, version, description)
    builder.block()
    return builder.build()
}
