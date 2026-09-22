package io.github.laretframework.model

/**
 * A named collection of related [Command]s.
 *
 * @param name    Primary group name (e.g. "file").
 * @param aliases Alternative group names accepted at the CLI (e.g. listOf("f")).
 * @param hidden  When `true`, the whole group is omitted from help, completion,
 *                docs, schema export and tool listings. It stays invocable, so
 *                internal or synthetic groups can exist without being part of
 *                the CLI's public face.
 */
data class CommandGroup(
    val name: String,
    val description: String = "",
    val commands: List<Command> = emptyList(),
    val aliases: List<String> = emptyList(),
    val hidden: Boolean = false,
) {
    fun matches(input: String): Boolean = input == name || input in aliases
}

/**
 * The groups and commands a user should be shown.
 *
 * Every user-facing listing goes through this: help, completion, generated
 * completion scripts, docs, schema export and the MCP tool list. Resolution
 * deliberately does not, so a hidden group stays invocable by name.
 */
fun List<CommandGroup>.visible(includeHidden: Boolean = false): List<CommandGroup> = if (includeHidden) {
    this
} else {
    filterNot { it.hidden }.map { group -> group.copy(commands = group.commands.filterNot { it.hidden }) }
}
