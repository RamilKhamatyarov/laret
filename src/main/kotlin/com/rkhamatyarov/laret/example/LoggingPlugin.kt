package com.rkhamatyarov.laret.example

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.plugin.LaretPlugin
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Example plugin: Logs all command executions with timing */
class LoggingPlugin : LaretPlugin {
    override val name = "LoggingPlugin"
    override val version = "1.0.0"

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val executionTimes = mutableMapOf<String, Long>()

    override fun initialize(app: CliApp) {
        System.err.println("Logging plugin initialized for app: ${app.name} v${app.version}")
        System.err.println("Total command groups: ${app.groups.size}")
        app.groups.forEach { group ->
            System.err.println("Group '${group.name}': ${group.commands.size} commands")
            group.commands.forEach { cmd -> System.err.println("    📌 ${cmd.name}: ${cmd.description}") }
        }
    }

    override fun beforeExecute(command: Command): Boolean {
        val timestamp = LocalDateTime.now().format(formatter)
        executionTimes[command.name] = System.currentTimeMillis()

        System.err.println("┌─────────────────────────────────────────────┐")
        System.err.println("│    COMMAND EXECUTION START")
        System.err.println("├─────────────────────────────────────────────┤")
        System.err.println("│ Timestamp: $timestamp")
        System.err.println("│ Command:   ${command.name}")
        System.err.println("│ Description: ${command.description}")
        System.err.println("│ Arguments: ${command.arguments.size}")
        System.err.println("│ Options:   ${command.options.size}")

        if (command.arguments.isNotEmpty()) {
            System.err.println("│")
            System.err.println("│ Arguments:")
            command.arguments.forEach { arg ->
                val req = if (arg.required) "REQUIRED" else "OPTIONAL"
                System.err.println("│   • ${arg.name.padEnd(20)} [$req] ${arg.description}")
            }
        }

        if (command.options.isNotEmpty()) {
            System.err.println("│")
            System.err.println("│ Options:")
            command.options.forEach { opt ->
                val takesVal = if (opt.takesValue) "(takes value)" else ""
                System.err.println(
                    "│   • -${opt.short}, --${opt.long.padEnd(15)} $takesVal ${opt.description}"
                )
            }
        }

        System.err.println("└─────────────────────────────────────────────┘")
        return true
    }

    override fun afterExecute(command: Command) {
        val elapsed =
            System.currentTimeMillis() - (executionTimes[command.name] ?: System.currentTimeMillis())
        val timestamp = LocalDateTime.now().format(formatter)

        System.err.println("┌─────────────────────────────────────────────┐")
        System.err.println("│   COMMAND EXECUTION COMPLETED")
        System.err.println("├─────────────────────────────────────────────┤")
        System.err.println("│   Timestamp: $timestamp")
        System.err.println("│   Command:   ${command.name}")
        System.err.println("│   Duration:  ${elapsed}ms")
        System.err.println("│   Status:    SUCCESS")
        System.err.println("└─────────────────────────────────────────────┘")

        executionTimes.remove(command.name)
    }

    override fun shutdown() {
        System.err.println("LoggingPlugin shutting down")
    }
}
