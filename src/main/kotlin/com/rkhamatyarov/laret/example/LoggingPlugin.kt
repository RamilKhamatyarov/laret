package com.rkhamatyarov.laret.example

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.plugin.LaretPlugin
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Example plugin: Logs all command executions with timing
 */
class LoggingPlugin : LaretPlugin {
    override val name = "LoggingPlugin"
    override val version = "1.0.0"

    private val log = LoggerFactory.getLogger(javaClass)
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val executionTimes = mutableMapOf<String, Long>()

    override fun initialize(app: CliApp) {
        log.info("Logging plugin initialized for app: ${app.name} v${app.version}")
        log.info("Total command groups: ${app.groups.size}")

        app.groups.forEach { group ->
            log.info("  📁 Group '${group.name}': ${group.commands.size} commands")
            group.commands.forEach { cmd ->
                log.info("    📌 ${cmd.name}: ${cmd.description}")
            }
        }
    }

    override fun beforeExecute(command: Command): Boolean {
        val timestamp = LocalDateTime.now().format(formatter)
        val key = "${command.name}_${System.currentTimeMillis()}"
        executionTimes[key] = System.currentTimeMillis()

        log.info("┌─────────────────────────────────────────────┐")
        log.info("│    COMMAND EXECUTION START")
        log.info("├─────────────────────────────────────────────┤")
        log.info("│ Timestamp: $timestamp")
        log.info("│ Command:  ${command.name}")
        log.info("│ Description: ${command.description}")
        log.info("│ Arguments: ${command.arguments.size}")
        log.info("│ Options: ${command.options.size}")

        if (command.arguments.isNotEmpty()) {
            log.info("│")
            log.info("│ Arguments:")
            command.arguments.forEach { arg ->
                val req = if (arg.required) "REQUIRED" else "OPTIONAL"
                log.info("│   • ${arg.name.padEnd(20)} [$req] ${arg.description}")
            }
        }

        if (command.options.isNotEmpty()) {
            log.info("│")
            log.info("│ Options:")
            command.options.forEach { opt ->
                val takesVal = if (opt.takesValue) "(takes value)" else ""
                log.info("│   • -${opt.short}, --${opt.long.padEnd(15)} $takesVal ${opt.description}")
            }
        }

        log.info("└─────────────────────────────────────────────┘")
        return true
    }

    override fun afterExecute(command: Command) {
        val elapsedMs = System.currentTimeMillis() - (executionTimes.values.lastOrNull() ?: System.currentTimeMillis())
        val timestamp = LocalDateTime.now().format(formatter)

        log.info("┌─────────────────────────────────────────────┐")
        log.info("│   COMMAND EXECUTION COMPLETED")
        log.info("├─────────────────────────────────────────────┤")
        log.info("│   Timestamp: $timestamp")
        log.info("│   Command:  ${command.name}")
        log.info("│   Duration: ${elapsedMs}ms")
        log.info("│   Status: SUCCESS")
        log.info("└─────────────────────────────────────────────┘")

        executionTimes.clear()
    }

    override fun shutdown() {
        log.info("LoggingPlugin shutting down")
    }
}
