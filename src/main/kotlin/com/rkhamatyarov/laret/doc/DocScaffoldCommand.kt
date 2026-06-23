package com.rkhamatyarov.laret.doc

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.model.Command
import java.nio.file.Files
import java.nio.file.Path

/**
 * Implements `laret doc scaffold`: walks the [app] tree and writes a prose
 * skeleton (`.md` with empty YAML frontmatter) for every command that does not
 * already have one under the output directory.
 *
 * The skeleton-building part is pure ([skeletonFor]); only [run] touches disk,
 * and it **never overwrites** an existing file — so re-running it is safe and
 * only fills in newly added commands.  Authors then point `doc generate` at the
 * same `docs/{lang}` resource root.
 */
class DocScaffoldCommand(private val app: CliApp) {

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
                if (Files.exists(target)) {
                    null
                } else {
                    target.parent?.let { Files.createDirectories(it) }
                    Files.writeString(target, skeletonFor(command), Charsets.UTF_8)
                    target
                }
            }
    }

    /** Builds the empty-frontmatter Markdown skeleton for [command]. */
    fun skeletonFor(command: Command): String = buildString {
        appendLine("---")
        appendLine("title: ${command.name}")
        appendLine("summary: ${command.description}")
        appendLine("synopsis:")
        appendLine("examples: []")
        appendLine("see_also: []")
        appendLine("---")
        appendLine()
        appendLine("<!-- Long description for `${command.name}` goes here. -->")
    }
}
