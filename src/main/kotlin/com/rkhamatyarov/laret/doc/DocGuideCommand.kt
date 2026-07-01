package com.rkhamatyarov.laret.doc

import com.rkhamatyarov.laret.model.fs.LaretFileSystem
import com.rkhamatyarov.laret.model.fs.RealFileSystem
import java.nio.file.Path

/**
 * Implements `laret doc guide <name>`: scaffolds a standalone guide page (e.g.
 * *Installation*, *Quick Start*) under `docs/{lang}/guides/<slug>.md` with a
 * minimal frontmatter
 * write long-form documentation that is not tied to a single command.
 *
 * All I/O is routed through [fs], so `doc guide <name> --dry-run` narrates the
 * write instead of touching disk — the command action never checks a dry-run
 * flag. The skeleton-building part ([skeletonFor]) is pure.
 */
class DocGuideCommand(private val fs: LaretFileSystem = RealFileSystem()) {

    /**
     * Write the guide skeleton for [name] under [outputDir], creating parent
     * directories as needed. When [lang] is non-blank the page is placed under
     * `{lang}/guides/`; otherwise directly under `guides/`.
     *
     * @return the path written.
     */
    fun create(name: String, outputDir: Path, lang: String? = null): Path {
        require(name.isNotBlank()) { "Guide name must not be blank" }
        val target = targetPath(name, outputDir, lang)
        target.parent?.let { fs.createDirectories(it) }
        fs.writeText(target, skeletonFor(slugify(name)))
        return target
    }

    /** Whether a guide page for [name] already exists (used to avoid clobbering authored guides). */
    fun exists(name: String, outputDir: Path, lang: String? = null): Boolean =
        fs.exists(targetPath(name, outputDir, lang))

    private fun targetPath(name: String, outputDir: Path, lang: String?): Path {
        val dir = if (lang.isNullOrBlank()) outputDir.resolve("guides") else outputDir.resolve("$lang/guides")
        return dir.resolve("${slugify(name)}.md")
    }

    /** Builds the minimal guide skeleton for the kebab-case [slug]. */
    fun skeletonFor(slug: String): String {
        val title = titleize(slug)
        return buildString {
            appendLine("---")
            appendLine("title: $title")
            appendLine("summary: TODO write a one-line summary for the $title guide")
            appendLine("---")
            appendLine()
            appendLine("# $title")
            appendLine()
            appendLine("TODO: Write guide.")
        }
    }

    /** `Quick Start` / `quick_start` → `quick-start`. */
    private fun slugify(name: String): String = name.trim().lowercase()
        .replace(Regex("[\\s_]+"), "-")
        .replace(Regex("[^a-z0-9-]"), "")
        .replace(Regex("-+"), "-")
        .trim('-')

    /** `quick-start` → `Quick Start`. */
    private fun titleize(slug: String): String = slug.split('-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
}
