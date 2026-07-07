package com.rkhamatyarov.laret.doc

import com.rkhamatyarov.laret.model.fs.LaretFileSystem
import com.rkhamatyarov.laret.model.fs.RealFileSystem
import java.nio.file.Path

/**
 * Implements `laret doc index`: turns the project `README.md` into the site
 * landing page `docs/index.md` (or `docs/{lang}/index.md`) by wrapping it in a
 * frontmatter block. The original README is **never** modified — only the copy
 * under the docs directory is written.
 *
 * The leading top-level `# Heading` of the README is dropped so it does not
 * duplicate the page title supplied via frontmatter. All I/O goes through [fs],
 * so `--dry-run` narrates the read/write instead of touching disk.
 */
class DocIndexCommand(private val fs: LaretFileSystem = RealFileSystem()) {

    /**
     * Read [readme], strip its leading H1, and write the result (with a
     * `title` frontmatter) to `index.md` under [outputDir]. When [lang] is
     * non-blank the file is placed under `{lang}/`.
     *
     * @return the path written.
     */
    fun fromReadme(readme: Path, outputDir: Path, lang: String? = null, title: String = "Laret"): Path {
        val body = stripLeadingH1(fs.readText(readme))
        val content = buildString {
            appendLine("---")
            appendLine("title: $title")
            appendLine("---")
            appendLine()
            append(body)
            if (!body.endsWith("\n")) appendLine()
        }
        val target = if (lang.isNullOrBlank()) outputDir.resolve("index.md") else outputDir.resolve("$lang/index.md")
        target.parent?.let { fs.createDirectories(it) }
        fs.writeText(target, content)
        return target
    }

    /**
     * Remove the first non-blank line if it is a top-level `# ` heading, along
     * with a single blank line immediately following it. Other headings are
     * left untouched.
     */
    internal fun stripLeadingH1(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").lines().toMutableList()
        val idx = lines.indexOfFirst { it.isNotBlank() }
        if (idx >= 0 && lines[idx].trimStart().startsWith("# ")) {
            lines.removeAt(idx)
            if (idx < lines.size && lines[idx].isBlank()) lines.removeAt(idx)
        }
        return lines.joinToString("\n").trim('\n')
    }
}
