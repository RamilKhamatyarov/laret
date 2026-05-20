package com.rkhamatyarov.laret.completion.generators

import com.rkhamatyarov.laret.model.Argument
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.Option

/**
 * Builds the JSON Schema "object" that describes a [Command]'s inputs.
 *
 * The shape `{ "type": "object", "properties": { … }, "required": [ … ] }` is
 * shared by OpenAI's `parameters` field and Anthropic's `input_schema` field,
 * so both exporters reuse it.
 *
 * Laret's [Option] model carries no explicit type or enum metadata, so the JSON
 * type is inferred: a value-less flag (`takesValue == false`) maps to `boolean`,
 * everything else maps to `string`.
 */
internal object JsonSchemaParameters {
    fun build(command: Command): Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to properties(command),
        "required" to requiredNames(command),
    )

    private fun properties(command: Command): Map<String, Any?> =
        command.options.associate { it.long to optionProperty(it) } +
            command.arguments.associate { it.name to argumentProperty(it) }

    private fun optionProperty(option: Option): Map<String, Any?> = buildMap {
        put("type", if (option.takesValue) "string" else "boolean")
        put("description", option.description.ifBlank { "The --${option.long} option." })
        if (option.default.isNotBlank()) put("default", option.default)
    }

    private fun argumentProperty(argument: Argument): Map<String, Any?> = buildMap {
        put("type", "string")
        put("description", argument.description.ifBlank { "The ${argument.name} argument." })
        if (argument.default.isNotBlank()) put("default", argument.default)
    }

    private fun requiredNames(command: Command): List<String> =
        command.arguments.filter { it.required && !it.optional }.map { it.name }
}
