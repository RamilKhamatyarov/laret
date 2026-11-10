package io.github.laretframework.completion.generators

import io.github.laretframework.core.CliApp
import io.github.laretframework.model.Command
import io.github.laretframework.model.CommandValueType
import java.util.Collections

/** Exports Laret's versioned, provider-neutral command contract. */
class LaretContractExporter : AbstractSchemaExporter() {
    override fun exportAsModel(app: CliApp): Map<String, Any?> = mapOf(
        "contractVersion" to "1",
        "name" to app.name,
        "version" to app.version,
        "commands" to mapCommands(app) { group, command ->
            mapOf(
                "name" to "${app.name}.${group.name}.${command.name}",
                "description" to describe(command),
                "inputSchema" to inputSchema(command),
                "impactProfile" to mapOf(
                    "category" to command.impactProfile.category.wireValue,
                    "capabilityNeeds" to command.impactProfile.capabilityNeeds,
                ),
            )
        },
    )

    private fun inputSchema(command: Command): Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to buildMap {
            command.options.forEach { option ->
                put(
                    option.long,
                    property(
                        if (option.takesValue) option.valueType else CommandValueType.BOOLEAN,
                        option.description.ifBlank { "The --${option.long} option." },
                        option.default,
                    ),
                )
            }
            command.arguments.forEach { argument ->
                put(
                    argument.name,
                    property(
                        argument.valueType,
                        argument.description.ifBlank { "The ${argument.name} argument." },
                        argument.default,
                    ),
                )
            }
        },
        "required" to requiredArguments(command),
    )

    private fun requiredArguments(command: Command): List<String> {
        val names = command.arguments.filter { it.required && !it.optional }.map { it.name }
        return if (names.isEmpty()) Collections.emptyList() else names
    }

    private fun property(type: CommandValueType, description: String, default: String): Map<String, Any?> = buildMap {
        put("type", type.jsonSchemaType)
        put("description", description)
        parseDefault(default, type)?.let { put("default", it) }
    }

    private fun parseDefault(value: String, type: CommandValueType): Any? {
        if (value.isBlank()) return null
        return when (type) {
            CommandValueType.STRING -> value
            CommandValueType.INTEGER -> value.toIntOrNull()
            CommandValueType.NUMBER -> value.toDoubleOrNull()
            CommandValueType.BOOLEAN -> value.toBooleanStrictOrNull()
        }
    }
}
