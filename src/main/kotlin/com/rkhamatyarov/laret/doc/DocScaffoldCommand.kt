package com.rkhamatyarov.laret.doc

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Command
import com.rkhamatyarov.laret.model.fs.LaretFileSystem
import com.rkhamatyarov.laret.model.fs.RealFileSystem
import java.nio.file.Path

/**
 * Implements `laret doc scaffold`: walks the [app] tree and writes a prose
 * skeleton (`.md` with YAML frontmatter) for every command that does not
 * already have one under the output directory.
 *
 * The skeleton-building part is pure ([skeletonFor]); only [run] touches disk,
 * and it **never overwrites** an existing file — so re-running it is safe and
 * only fills in newly added commands.  Authors then point `doc generate` at the
 * same `docs/{lang}` resource root.
 *
 * All I/O is routed through [fs], so `doc scaffold --dry-run` narrates what it
 * *would* create without touching disk; the command action stays free of any
 * dry-run branching.
 */
class DocScaffoldCommand(private val app: CliApp, private val fs: LaretFileSystem = RealFileSystem()) {

    /**
     * Create missing skeletons under [outputDir] for [lang].
     *
     * @param includeHidden When `true`, hidden commands get skeletons too.
     * @return The paths of the files actually created (existing files are skipped).
     */
    fun run(lang: String, outputDir: Path, includeHidden: Boolean = false): List<Path> = app.groups.flatMap { group ->
        group.commands
            .filter { includeHidden || !it.hidden }
            .mapNotNull { command ->
                val target = outputDir.resolve("$lang/${group.name}/${command.name}.md")
                if (fs.exists(target)) {
                    null
                } else {
                    target.parent?.let { fs.createDirectories(it) }
                    fs.writeText(target, skeletonFor(command, usageFor(group.name, command)))
                    target
                }
            }
    }

    /**
     * Builds the Markdown skeleton for [command], pre-filling `synopsis` with the
     * generated [usage] line and leaving a `TODO` marker in the body so authors
     * can see at a glance which pages still need real content.
     */
    fun skeletonFor(command: Command, usage: String = ""): String = buildString {
        appendLine("---")
        appendLine("title: ${command.name}")
        appendLine("summary: ${command.description}")
        appendLine("synopsis: $usage")
        appendLine("examples: []")
        appendLine("see_also: []")
        appendLine("---")
        appendLine()
        appendLine("<!-- TODO: Write detailed explanation and examples for `${command.name}`. -->")
    }

    /** Renders a default usage line, e.g. `laret file create <path> [--force]` (arguments only). */
    private fun usageFor(groupName: String, command: Command): String {
        val args = command.arguments.joinToString(" ") { arg ->
            if (arg.required && !arg.optional) "<${arg.name}>" else "[${arg.name}]"
        }
        return listOf(app.name, groupName, command.name, args)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}
