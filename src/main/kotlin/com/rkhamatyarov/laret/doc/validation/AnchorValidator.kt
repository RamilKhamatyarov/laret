package com.rkhamatyarov.laret.doc.validation

/**
 * Extracts ATX headings from Markdown and validates **local** (`#anchor`) links
 * against their MkDocs-compatible slugs.
 *
 * Per the approved design, only same-file anchors are checked here; cross-file
 * anchors (`other.md#x`) are delegated to MkDocs. Regex + stdlib only, so it is
 * GraalVM Native-Image safe.
 */
object AnchorValidator {

    private val HEADING = Regex("(?m)^#{1,6}\\s+(.+?)\\s*#*\\s*$")
    private val NON_SLUG_CHARS = Regex("[^a-z0-9 -]")
    private val WHITESPACE = Regex("\\s+")

    /**
     * MkDocs / Python-Markdown style slug: lowercase, drop punctuation, collapse
     * runs of whitespace to a single `-`. E.g. `"See Also"` → `"see-also"`.
     */
    fun slugify(heading: String): String = NON_SLUG_CHARS.replace(heading.lowercase(), "")
        .trim()
        .let { WHITESPACE.replace(it, "-") }

    /** Slugs of every heading in [markdown] (code spans are ignored). */
    fun headingSlugs(markdown: String): Set<String> {
        val body = MarkdownLinkExtractor.stripCode(markdown)
        return HEADING.findAll(body)
            .map { slugify(it.groupValues[1]) }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** True when [anchor] (without the leading `#`) matches a heading slug in [markdown]. */
    fun hasAnchor(markdown: String, anchor: String): Boolean = anchor.lowercase() in headingSlugs(markdown)
}
