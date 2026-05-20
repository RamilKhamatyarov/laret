package com.rkhamatyarov.laret.completion.generators

import com.rkhamatyarov.laret.core.CliApp

/**
 * Exports the command tree as Anthropic tool definitions: a top-level `tools`
 * array whose entries use `input_schema` (Anthropic's equivalent of OpenAI's
 * `parameters`).
 */
class AnthropicSchemaExporter : AbstractSchemaExporter() {
    override fun exportAsModel(app: CliApp): Map<String, Any?> = mapOf(
        "tools" to
            mapCommands(app) { group, command ->
                mapOf(
                    "name" to functionName(app, group, command),
                    "description" to describe(command),
                    "input_schema" to parameters(command),
                )
            },
    )
}
