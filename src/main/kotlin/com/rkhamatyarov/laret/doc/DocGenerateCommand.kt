package com.rkhamatyarov.laret.doc

import com.rkhamatyarov.laret.core.CliApp
import com.rkhamatyarov.laret.doc.generators.GroffDocGenerator
import com.rkhamatyarov.laret.doc.generators.MarkdownDocGenerator
import com.rkhamatyarov.laret.doc.prose.ProseProvider
import com.rkhamatyarov.laret.doc.prose.ResourceProseProvider
import java.nio.file.Files
import java.nio.file.Path

/**
 * Orchestrates documentation generation for `laret doc generate`.
 *
 * This is the **only** layer that performs file I/O: it selects a pure
 * [DocGenerator] for the requested [DocFormat], fans out over the requested
 * language(s), then writes the resulting [DocFile]s under the output directory.
 *
 * Usage: `laret doc generate --format <md|man> --lang <en|es|all> --output-dir <path>`.
 */
class DocGenerateCommand(private val app: CliApp) {

    /**
     * Generate and write documentation.
     *
     * @param format    Output format (Markdown or man pages).
     * @param lang      Language tag, or [ALL] to emit every supported language.
     * @param outputDir Directory under which [DocFile.relativePath]s are written.
     * @param provider  Prose source (overridable for tests).
     * @return The list of files actually written, in generation order.
     */
    fun run(
        format: DocFormat,
        lang: String,
        outputDir: Path,
        provider: ProseProvider = ResourceProseProvider(),
    ): List<Path> {
        val generator: DocGenerator = when (format) {
            DocFormat.MARKDOWN -> MarkdownDocGenerator(provider)
            DocFormat.MAN -> GroffDocGenerator(provider)
        }

        val languages = resolveLanguages(format, lang)
        val files = languages
            .flatMap { generator.generate(app, it) }
            .distinctBy { it.relativePath }

        return files.map { write(outputDir, it) }
    }

    /**
     * Man pages are language-neutral (flat `man1/`), so they are emitted once.
     * Markdown fans out: [ALL] expands to every supported language.
     */
    private fun resolveLanguages(format: DocFormat, lang: String): List<String> = when {
        format == DocFormat.MAN -> listOf(
            if (lang == ALL || lang.isBlank()) ResourceProseProvider.FALLBACK_LANG else lang,
        )
        lang == ALL -> SUPPORTED_LANGUAGES
        else -> listOf(lang)
    }

    private fun write(outputDir: Path, docFile: DocFile): Path {
        val target = outputDir.resolve(docFile.relativePath)
        target.parent?.let { Files.createDirectories(it) }
        Files.writeString(target, docFile.content, Charsets.UTF_8)
        return target
    }

    companion object {
        const val ALL = "all"

        /** Languages emitted by `--lang all`, aligned with the repo's i18n bundles. */
        val SUPPORTED_LANGUAGES = listOf("en", "es")
    }
}
