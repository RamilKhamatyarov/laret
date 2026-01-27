package com.rkhamatyarov.laret.model

/**
 * Represents a group of related commands
 */
data class CommandGroup(
    val name: String,
    val description: String = "",
    val commands: List<Command> = emptyList(),
)
