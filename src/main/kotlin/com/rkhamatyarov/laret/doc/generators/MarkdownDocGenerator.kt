package com.rkhamatyarov.laret.doc.generators

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.doc.DocFile
import com.rkhamatyarov.laret.doc.DocFormat
import com.rkhamatyarov.laret.doc.DocGenerator
import com.rkhamatyarov.laret.doc.prose.Prose
import com.rkhamatyarov.laret.doc.prose.ProseProvider
import com.rkhamatyarov.laret.doc.prose.ResourceProseProvider
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.CommandGroup

/**
 * Generates a hierarchical Markdown tree suitable for MkDocs:
 *
 * ```
 * {lang}/index.md                ← lists groups
 * {lang}/{group}/index.md        ← lists the group's commands
 * {lang}/{group}/{command}.md    ← one page per command
 * ```
 *
 * Pure: [generate] returns [DocFile]s without any file I/O.  Prose (long
 * description, examples, see-also, synopsis) is supplied by an injected
 * [ProseProvider]; the generator only formats Markdown.
 *
 * Hidden commands are skipped unless `includeHidden` is set, in which case their
 * page is emitted with an `[INTERNAL]` badge but they are still left out of the
 * `index.md` listings and the [mkdocsYaml] navigation.
 */
class MarkdownDocGenerator(private val prose: ProseProvider = ResourceProseProvider()) : DocGenerator {

    override val format: DocFormat = DocFormat.MARKDOWN

    override fun generate(app: CliApp, lang: String, includeHidden: Boolean): List<DocFile> = buildList {
        app.groups.forEach { group ->
            documentedCommands(group, includeHidden).forEach { command ->
                val resolved = prose.resolve(group.name, command, lang)
                add(
                    DocFile(
                        relativePath = "$lang/${group.name}/${command.name}.md",
                        content = render(app, group.name, command, resolved),
                    ),
                )
            }
            add(DocFile("$lang/${group.name}/index.md", groupIndex(group)))
        }
        add(DocFile("$lang/index.md", rootIndex(app)))
    }

    /**
     * Renders an `mkdocs.yml` whose `nav` mirrors the [app] tree across every
     * [languages] entry.  Hidden commands are always excluded from navigation.
     * This is emitted once by the orchestrator, not per-language, so the nav is
     * complete for `--lang all`.
     */
    fun mkdocsYaml(app: CliApp, languages: List<String>): DocFile {
        val yaml = buildString {
            appendLine("site_name: ${app.name}")
            appendLine("nav:")
            languages.forEach { lang ->
                appendLine("  - $lang:")
                appendLine("      - Home: $lang/index.md")
                app.groups.forEach { group ->
                    appendLine("      - ${group.name}:")
                    appendLine("          - Overview: $lang/${group.name}/index.md")
                    documentedCommands(group, includeHidden = false).forEach { command ->
                        appendLine("          - ${command.name}: $lang/${group.name}/${command.name}.md")
                    }
                }
            }
        }.trimEnd() + "\n"
        return DocFile("mkdocs.yml", yaml)
    }

    /** Commands that should be documented: visible ones always, hidden ones only on demand. */
    private fun documentedCommands(group: CommandGroup, includeHidden: Boolean): List<Command> =
        group.commands.filter { includeHidden || !it.hidden }

    private fun render(app: CliApp, groupName: String, command: Command, p: Prose): String = buildString {
        appendLine("# ${p.title}")
        appendLine()
        if (command.hidden) {
            appendLine("> **[INTERNAL]** Hidden command — excluded from navigation.")
            appendLine()
        }
        appendLine(p.summary)
        appendLine()

        if (command.aliases.isNotEmpty()) {
            appendLine("**Aliases:** ${command.aliases.joinToString(", ") { "`$it`" }}")
            appendLine()
        }

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

    private fun rootIndex(app: CliApp): String = buildString {
        appendLine("# ${app.name}")
        appendLine()
        appendLine(app.description.ifBlank { "Command reference." })
        appendLine()
        appendLine("## Command Groups")
        appendLine()
        app.groups.forEach { group ->
            val desc = group.description.ifBlank { "No description." }
            appendLine("- [${group.name}](${group.name}/index.md) — $desc")
        }
    }.trimEnd() + "\n"

    private fun groupIndex(group: CommandGroup): String = buildString {
        appendLine("# ${group.name}")
        appendLine()
        appendLine(group.description.ifBlank { "No description." })
        appendLine()
        appendLine("## Commands")
        appendLine()
        documentedCommands(group, includeHidden = false).forEach { command ->
            val desc = command.description.ifBlank { "No description." }
            appendLine("- [${command.name}](${command.name}.md) — $desc")
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
