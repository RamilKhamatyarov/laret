package io.github.laretframework.completion

import io.github.laretframework.model.Argument
import io.github.laretframework.model.Command
import io.github.laretframework.model.CommandValueType
import io.github.laretframework.model.ImpactProfile
import io.github.laretframework.model.Option
import java.util.Collections

class McpToolMapper {
    fun mapCommand(command: Command, prefix: String = ""): McpTool {
        val name = listOf(prefix, command.name).filter { it.isNotBlank() }.joinToString(".")
        return McpTool(
            name = name,
            description = command.description.ifBlank { "Execute the $name command." },
            inputSchema = buildInputSchema(command),
            impactProfile = command.impactProfile,
        )
    }

    fun buildInputSchema(command: Command): Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to buildProperties(command),
        "required" to requiredArguments(command),
    )

    private fun requiredArguments(command: Command): List<String> {
        val names = command.arguments.filter { it.required && !it.optional }.map { it.name }
        return if (names.isEmpty()) Collections.emptyList() else names
    }

    private fun buildProperties(command: Command): Map<String, Any?> =
        command.options.associate { it.long to optionSchema(it) } +
            command.arguments.associate { it.name to argumentSchema(it) }

    private fun optionSchema(option: Option): Map<String, Any?> = buildMap {
        put("type", if (option.takesValue) option.valueType.jsonSchemaType else "boolean")
        put("description", option.description.ifBlank { "The --${option.long} option." })
        typedDefault(option.default, if (option.takesValue) option.valueType else CommandValueType.BOOLEAN)?.let {
            put("default", it)
        }
    }

    private fun argumentSchema(argument: Argument): Map<String, Any?> = buildMap {
        put("type", argument.valueType.jsonSchemaType)
        put("description", argument.description.ifBlank { "The ${argument.name} argument." })
        typedDefault(argument.default, argument.valueType)?.let { put("default", it) }
    }

    private fun typedDefault(value: String, type: CommandValueType): Any? {
        if (value.isBlank()) return null
        return when (type) {
            CommandValueType.STRING -> value
            CommandValueType.INTEGER -> value.toIntOrNull()
            CommandValueType.NUMBER -> value.toDoubleOrNull()
            CommandValueType.BOOLEAN -> value.toBooleanStrictOrNull()
        }
    }
}

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?>,
    val outputSchema: Map<String, Any?>? = null,
    val impactProfile: ImpactProfile = ImpactProfile(),
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("name", name)
        put("description", description)
        put("inputSchema", inputSchema)
        if (outputSchema != null) put("outputSchema", outputSchema)
        put(
            "impactProfile",
            mapOf(
                "category" to impactProfile.category.wireValue,
                "capabilityNeeds" to impactProfile.capabilityNeeds,
            ),
        )
    }
}
