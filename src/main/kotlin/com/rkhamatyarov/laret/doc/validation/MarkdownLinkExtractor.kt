package com.rkhamatyarov.laret.doc.validation

/**
 * A single Markdown link `[text](target)`, classified by the kind of [target].
 *
 * A target may carry an anchor (`page.md#section` or just `#section`); [filePart]
 * and [anchorPart] split it so callers can validate the file and the anchor
 * independently.
 */
data class MarkdownLink(val text: String, val target: String) {

    /** `http(s)://…` or `mailto:` — never resolved against the local tree. */
    val isExternal: Boolean
        get() = target.startsWith("http://") ||
            target.startsWith("https://") ||
            target.startsWith("mailto:")

    /** `#section` with no file component → a local anchor in the current page. */
    val isAnchorOnly: Boolean get() = target.startsWith("#")

    /** The file portion before any `#anchor` (empty for anchor-only links). */
    val filePart: String get() = target.substringBefore("#")

    /** The anchor portion after `#` (empty when the link has none). */
    val anchorPart: String get() = target.substringAfter("#", "")

    /** An internal link that points at a Markdown page (optionally with an anchor). */
    val isInternalMarkdown: Boolean get() = !isExternal && filePart.endsWith(".md")
}

/**
 * Extracts Markdown links with a regex, after **stripping code** so that fenced
 * (```` ``` ````) and inline (`` `code` ``) spans never produce false positives.
 *
 * Deliberately stdlib-only: no external Markdown parser, and no reflection or
 * dynamic proxies, so it is GraalVM Native-Image safe.
 */
object MarkdownLinkExtractor {

    private val FENCED_CODE = Regex("(?s)```.*?```")
    private val INLINE_CODE = Regex("`[^`\\n]*`")
    private val LINK = Regex("""\[(.*?)]\(\s*([^)\s]+)(?:\s+"[^"]*")?\s*\)""")

    /** Removes fenced and inline code spans so their contents are ignored. */
    fun stripCode(markdown: String): String = markdown.replace(FENCED_CODE, "").replace(INLINE_CODE, "")

    /** Every `[text](target)` link outside of code spans, in document order. */
    fun extract(markdown: String): List<MarkdownLink> = LINK.findAll(stripCode(markdown))
        .map { MarkdownLink(it.groupValues[1], it.groupValues[2].trim()) }
        .toList()
}
