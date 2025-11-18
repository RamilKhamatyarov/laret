package com.rkhamatyarov.laret.dsl

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.CommandGroup

/**
 * Builds CLI application
 */
class CliBuilder(
    val name: String,
    val version: String,
    val description: String,
) {
    private val groups = mutableListOf<CommandGroup>()

    /**
     * Define a group of related commands
     */
    fun group(
        name: String,
        description: String = "",
        block: GroupBuilder.() -> Unit,
    ) {
        val groupBuilder = GroupBuilder(name, description)
        groupBuilder.block()
        groups.add(groupBuilder.build())
    }

    fun build(): CliApp = CliApp(name, version, description, groups)
}
