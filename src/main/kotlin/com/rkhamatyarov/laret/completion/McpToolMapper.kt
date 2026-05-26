package com.rkhamatyarov.laret.completion

import com.rkhamatyarov.laret.model.Argument
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.Option

class McpToolMapper {
    fun mapCommand(command: Command, prefix: String = ""): McpTool {
        val name = listOf(prefix, command.name).filter { it.isNotBlank() }.joinToString(".")
        return McpTool(
            name = name,
            description = command.description.ifBlank { "Execute the $name command." },
            inputSchema = buildInputSchema(command),
        )
    }

    fun buildInputSchema(command: Command): Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to buildProperties(command),
        "required" to command.arguments.filter { it.required && !it.optional }.map { it.name },
    )

    private fun buildProperties(command: Command): Map<String, Any?> =
        command.options.associate { it.long to optionSchema(it) } +
            command.arguments.associate { it.name to argumentSchema(it) }

    private fun optionSchema(option: Option): Map<String, Any?> = buildMap {
        put("type", if (option.takesValue) inferValueType(option.default) else "boolean")
        put("description", option.description.ifBlank { "The --${option.long} option." })
        if (option.default.isNotBlank()) put("default", coerceDefault(option.default))
    }

    private fun argumentSchema(argument: Argument): Map<String, Any?> = buildMap {
        put("type", inferValueType(argument.default))
        put("description", argument.description.ifBlank { "The ${argument.name} argument." })
        if (argument.default.isNotBlank()) put("default", coerceDefault(argument.default))
    }

    private fun inferValueType(value: String): String = when {
        value.toIntOrNull() != null -> "integer"
        value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true) -> "boolean"
        else -> "string"
    }

    private fun coerceDefault(value: String): Any = value.toIntOrNull()
        ?: value.toBooleanStrictOrNull()
        ?: value
}

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?>,
    val outputSchema: Map<String, Any?>? = null,
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("name", name)
        put("description", description)
        put("inputSchema", inputSchema)
        if (outputSchema != null) put("outputSchema", outputSchema)
    }
}
