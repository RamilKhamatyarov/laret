package com.rkhamatyarov.laret.doc.validation

import com.rkhamatyarov.laret.doc.DocFile

/**
 * Validates a set of **rendered** Markdown [DocFile]s — the exact content that
 * will ship — rather than the raw prose sources.
 *
 * - Broken internal `.md` links (target not among the generated pages) → error.
 * - Broken local `#anchor`s (no matching heading in the same page) → error.
 * - External links are recognised but not network-verified.
 * - Cross-file anchors (`other.md#x`) validate only the file part; the anchor is
 *   delegated to MkDocs.
 *
 * Pure (no I/O), regex-only, GraalVM Native-Image safe.
 */
object DocValidator {

    fun validate(files: List<DocFile>): ValidationReport {
        val markdown = files.filter { it.relativePath.endsWith(".md") }
        if (markdown.isEmpty()) return ValidationReport.EMPTY

        val resolver = LinkResolver(markdown.map { it.relativePath }.toSet())
        val errors = mutableListOf<String>()

        markdown.forEach { file ->
            MarkdownLinkExtractor.extract(file.content).forEach { link ->
                when {
                    link.target.isBlank() || link.isExternal -> Unit
                    link.isAnchorOnly ->
                        if (!AnchorValidator.hasAnchor(file.content, link.anchorPart)) {
                            errors += "broken anchor '#${link.anchorPart}' in ${file.relativePath}"
                        }
                    link.isInternalMarkdown -> {
                        val resolved = resolver.resolve(file.relativePath, link.filePart)
                        if (resolved == null || !resolver.isKnown(resolved)) {
                            errors += "broken link '${link.target}' in ${file.relativePath}"
                        }
                    }
                    else -> Unit
                }
            }
        }
        return ValidationReport(errors = errors)
    }
}
