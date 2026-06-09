package com.rkhamatyarov.laret.doc.generators

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.doc.DocFile
import com.rkhamatyarov.laret.doc.DocFormat
import com.rkhamatyarov.laret.doc.DocGenerator
import com.rkhamatyarov.laret.doc.prose.Prose
import com.rkhamatyarov.laret.doc.prose.ProseProvider
import com.rkhamatyarov.laret.doc.prose.ResourceProseProvider
import com.rkhamatyarov.laret.model.Command

/**
 * Generates a directory-based Markdown tree: `{lang}/{group}/{command}.md`,
 * one file per command.  Pure: returns [DocFile]s without any file I/O.
 *
 * Prose (long description, examples, see-also, synopsis) is supplied by an
 * injected [ProseProvider]; the generator only formats Markdown.
 */
class MarkdownDocGenerator(private val prose: ProseProvider = ResourceProseProvider()) : DocGenerator {

    override val format: DocFormat = DocFormat.MARKDOWN

    override fun generate(app: CliApp, lang: String): List<DocFile> = app.groups.flatMap { group ->
        group.commands.map { command ->
            val resolved = prose.resolve(group.name, command, lang)
            DocFile(
                relativePath = "$lang/${group.name}/${command.name}.md",
                content = render(app, group.name, command, resolved),
            )
        }
    }

    private fun render(app: CliApp, groupName: String, command: Command, p: Prose): String = buildString {
        appendLine("# ${p.title}")
        appendLine()
        appendLine(p.summary)
        appendLine()

        appendLine("## Synopsis")
        appendLine()
        appendLine("```")
        appendLine(p.synopsis ?: defaultSynopsis(app, groupName, command))
        appendLine("```")
        appendLine()

        appendLine("## Description")
        appendLine()
        appendLine(p.body.ifBlank { p.summary })
        appendLine()

        if (command.arguments.isNotEmpty()) {
            appendLine("## Arguments")
            appendLine()
            command.arguments.forEach { arg ->
                val req = if (arg.required) "required" else "optional"
                val desc = arg.description.ifBlank { "No description." }
                appendLine("- `<${arg.name}>` ($req) — $desc")
            }
            appendLine()
        }

        if (command.options.isNotEmpty()) {
            appendLine("## Options")
            appendLine()
            command.options.forEach { opt ->
                val desc = buildString {
                    append(opt.description.ifBlank { "No description." })
                    if (opt.default.isNotBlank()) append(" Default: `${opt.default}`.")
                }
                appendLine("- `-${opt.short}, --${opt.long}` — $desc")
            }
            appendLine()
        }

        if (p.examples.isNotEmpty()) {
            appendLine("## Examples")
            appendLine()
            appendLine("```")
            p.examples.forEach { appendLine(it) }
            appendLine("```")
            appendLine()
        }

        if (p.seeAlso.isNotEmpty()) {
            appendLine("## See Also")
            appendLine()
            p.seeAlso.forEach { appendLine("- $it") }
            appendLine()
        }
    }.trimEnd() + "\n"

    private fun defaultSynopsis(app: CliApp, groupName: String, command: Command): String = buildList {
        add(app.name)
        if (groupName.isNotBlank()) add(groupName)
        add(command.name)
        command.arguments.forEach { add(if (it.required) "<${it.name}>" else "[<${it.name}>]") }
        command.options.forEach { add(if (it.takesValue) "[--${it.long} <value>]" else "[--${it.long}]") }
    }.joinToString(" ")
}
