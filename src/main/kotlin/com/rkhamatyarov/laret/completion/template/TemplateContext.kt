package com.rkhamatyarov.laret.completion.template

data class TemplateContext(
    val appName: String,
    val groups: List<GroupContext>,
    val globalOptions: List<OptionContext> = emptyList()
) {
    data class GroupContext(val name: String, val commands: List<CommandContext>) {
        fun toMap(): Map<String, Any?> = mapOf(
            "name" to name,
            "commands" to commands.map { it.toMap() }
        )
    }

    data class CommandContext(val name: String, val description: String = "", val options: List<OptionContext>) {
        fun toMap(): Map<String, Any?> = mapOf(
            "name" to name,
            "description" to description,
            "options" to options.map { it.toMap() }
        )
    }

    data class OptionContext(val long: String, val short: String, val description: String = "") {
        fun toMap(): Map<String, Any?> = mapOf(
            "long" to long,
            "short" to short,
            "description" to description
        )
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "appName" to appName,
        "groups" to groups.map { it.toMap() },
        "globalOptions" to globalOptions.map { it.toMap() }
    )
}
