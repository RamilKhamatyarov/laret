package io.github.laretframework.completion

import io.github.laretframework.completion.generators.AnthropicSchemaExporter
import io.github.laretframework.completion.generators.OpenAiSchemaExporter
import io.github.laretframework.core.CliApp
import java.io.File

/**
 * Generates an LLM function-calling schema for [app] and, optionally, writes it
 * to a file.
 *
 * Mirrors the [CompletionCommand] pattern: the exporters stay pure, while file
 * I/O is isolated here.
 */
class SchemaExportCommand(private val app: CliApp) {
    /**
     * @param format     target schema dialect.
     * @param outputFile destination file, or `null` to only return the text.
     * @return the generated schema as a JSON string.
     */
    fun export(format: SchemaFormat, outputFile: File? = null): String {
        val schema = exporterFor(format).export(app)
        outputFile?.writeText(schema)
        return schema
    }

    private fun exporterFor(format: SchemaFormat): SchemaExporter = when (format) {
        SchemaFormat.OPENAI -> OpenAiSchemaExporter()
        SchemaFormat.ANTHROPIC -> AnthropicSchemaExporter()
    }
}
