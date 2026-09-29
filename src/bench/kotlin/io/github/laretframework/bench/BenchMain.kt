package io.github.laretframework.bench

import io.github.laretframework.core.CliApp
import io.github.laretframework.dsl.cli
import io.github.laretframework.example.BenchCommands
import kotlin.system.exitProcess

fun buildBenchApp(): CliApp = cli(
    name = "laret-bench",
    version = "bench",
    description = "Laret concurrency benchmark payloads",
) {
    group(name = "bench", description = "Concurrency benchmark payloads") {
        BenchCommands.register(this)
    }
}

fun main(args: Array<String>) {
    exitProcess(buildBenchApp().run(args))
}
