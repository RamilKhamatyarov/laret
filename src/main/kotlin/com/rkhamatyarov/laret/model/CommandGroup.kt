package com.rkhamatyarov.laret.model

/**
 * Represents a group of related commands
 */
data class CommandGroup(
    val name: String,
    val description: String = "",
    val commands: List<Command> = emptyList()
) {
    fun showHelp() {
        println("Group: $name - $description")
        println("\nCommands:")
        commands.forEach { cmd ->
            println("  ${cmd.name.padEnd(20)} ${cmd.description}")
        }
    }
}