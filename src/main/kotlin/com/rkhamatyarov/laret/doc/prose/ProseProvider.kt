package com.rkhamatyarov.laret.doc.prose

import com.rkhamatyarov.laret.model.Command

/**
 * Resolved, ready-to-render prose for a single command.
 *
 * All fields are *effective* values: a [ProseProvider] has already applied any
 * fallback to the DSL [Command.description], so generators can consume this
 * object directly without further null-handling beyond [synopsis].
 *
 * @param title   Page title (frontmatter `title`, else the command name).
 * @param summary Short one-line description (frontmatter `summary`, else the
 *                DSL [Command.description]).
 * @param synopsis Optional manual usage line (frontmatter `synopsis`); when
 *                 `null`, generators build a default from the command model.
 * @param examples Usage examples (frontmatter `examples`), possibly empty.
 * @param seeAlso  Cross-reference page names (frontmatter `see_also`), possibly empty.
 * @param body     Long-description Markdown body (text after the frontmatter),
 *                 possibly blank.
 */
data class Prose(
    val title: String,
    val summary: String,
    val synopsis: String?,
    val examples: List<String>,
    val seeAlso: List<String>,
    val body: String,
)

/**
 * Supplies localized long-form prose and metadata for commands.
 *
 * The DSL only carries a short [Command.description]; richer documentation
 * (long description, examples, see-also, synopsis) lives in external,
 * per-language files loaded by implementations such as [ResourceProseProvider].
 */
interface ProseProvider {
    /**
     * Resolve prose for [command] in group [groupName] for language [lang].
     *
     * Implementations apply graceful fallback (requested language → English →
     * DSL description) and never throw for missing or malformed content.
     */
    fun resolve(groupName: String, command: Command, lang: String): Prose
}
