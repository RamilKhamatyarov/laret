package com.rkhamatyarov.laret.model

/**
 * A named collection of related [Command]s.
 *
 * @param name    Primary group name (e.g. "file").
 * @param aliases Alternative group names accepted at the CLI (e.g. listOf("f")).
 */
data class CommandGroup(
    val name: String,
    val description: String = "",
    val commands: List<Command> = emptyList(),
    val aliases: List<String> = emptyList()
) {
    fun matches(input: String): Boolean = input == name || input in aliases
}
