package com.rkhamatyarov.laret.completion.generators

import com.rkhamatyarov.laret.core.CliApp

/**
 * Exports the command tree as OpenAI function-calling definitions: a top-level
 * `functions` array of `{ "type": "function", "function": { … } }` objects.
 */
class OpenAiSchemaExporter : AbstractSchemaExporter() {
    override fun exportAsModel(app: CliApp): Map<String, Any?> = mapOf(
        "functions" to
            mapCommands(app) { group, command ->
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to functionName(app, group, command),
                        "description" to describe(command),
                        "parameters" to parameters(command),
                    ),
                )
            },
    )
}
