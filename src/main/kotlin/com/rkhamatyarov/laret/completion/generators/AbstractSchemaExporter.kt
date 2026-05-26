package com.rkhamatyarov.laret.completion.generators

import com.rkhamatyarov.laret.completion.SchemaExporter
import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup
import com.rkhamatyarov.laret.output.OutputFormat

/**
 * Shared behaviour for [SchemaExporter] implementations (Template Method).
 *
 * Concrete exporters decide only how to wrap each command in their
 * provider-specific envelope; tree traversal, function naming and JSON
 * serialisation live here.
 */
abstract class AbstractSchemaExporter : SchemaExporter {
    override fun export(app: CliApp): String = OutputFormat.asJson(exportAsModel(app))

    /** Fully-qualified, LLM-safe function name: `app_group_command`. */
    protected fun functionName(app: CliApp, group: CommandGroup, command: Command): String =
        "${app.name}_${group.name}_${command.name}"

    /** Command description, falling back to generated text when blank. */
    protected fun describe(command: Command): String =
        command.description.ifBlank { "Execute the ${command.name} command." }

    /** JSON Schema parameters object for the command's options and arguments. */
    protected fun parameters(command: Command): Map<String, Any?> = JsonSchemaParameters.build(command)

    /** Maps every command in every group to a provider-specific entry. */
    protected fun mapCommands(
        app: CliApp,
        transform: (CommandGroup, Command) -> Map<String, Any?>,
    ): List<Map<String, Any?>> = app.groups.flatMap { group ->
        group.commands.map { command -> transform(group, command) }
    }
}
