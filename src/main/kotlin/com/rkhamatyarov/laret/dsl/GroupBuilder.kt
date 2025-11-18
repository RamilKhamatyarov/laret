package com.rkhamatyarov.laret.dsl

import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup

/**
 * Builds a command group
 */
class GroupBuilder(
    val name: String,
    val description: String = ""
) {
    private val commands = mutableListOf<Command>()

    /**
     * Define a command within a group
     */
    fun command(
        name: String,
        description: String = "",
        block: CommandBuilder.() -> Unit
    ) {
        val cmdBuilder = CommandBuilder(name, description)
        cmdBuilder.block()
        commands.add(cmdBuilder.build())
    }

    fun build(): CommandGroup =
        CommandGroup(name, description, commands)
}
