package com.rkhamatyarov.laret.model

import org.slf4j.LoggerFactory

/**
 * Represents a group of related commands
 */
data class CommandGroup(
    val name: String,
    val description: String = "",
    val commands: List<Command> = emptyList(),
) {
    private val log = LoggerFactory.getLogger(javaClass::class.java)

    fun showHelp() {
        log.info("Group: $name - $description")
        log.info("\nCommands:")
        commands.forEach { cmd ->
            log.info("  ${cmd.name.padEnd(20)} ${cmd.description}")
        }
    }
}
